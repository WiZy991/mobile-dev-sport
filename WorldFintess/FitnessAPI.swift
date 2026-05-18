import Foundation

enum FitnessAPIError: Error, LocalizedError {
    case invalidURL
    case http(Int, String?)
    case decoding(Error)
    case emptyBody
    case refreshFailed

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Некорректный URL"
        case .http(let code, let body): return body ?? "HTTP \(code)"
        case .decoding(let e): return "Ошибка разбора данных: \(e.localizedDescription)"
        case .emptyBody: return "Пустой ответ сервера"
        case .refreshFailed: return "Сессия истекла. Войдите снова."
        }
    }
}

/// Низкоуровневый HTTP-клиент: заголовки как в Android (`Bearer` + `X-User-Id`), при 401 — одна попытка `auth/refresh`.
final class FitnessAPI: @unchecked Sendable {
    private let baseRoot: URL
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private let lock = NSLock()
    private var accessToken: String?
    private var refreshToken: String?
    private var userId: String?

    init() {
        guard let u = URL(string: AppConfiguration.apiBaseURLString) else {
            fatalError("Invalid AppConfiguration.apiBaseURLString")
        }
        baseRoot = u
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        session = URLSession(configuration: config)
        decoder = AppJSON.decoder()
        encoder = AppJSON.encoder()
    }

    func setCredentials(access: String?, refresh: String?, userId: String?) {
        lock.lock()
        defer { lock.unlock() }
        accessToken = access
        refreshToken = refresh
        self.userId = userId
    }

    func snapshotCredentials() -> (access: String?, refresh: String?, userId: String?) {
        lock.lock()
        defer { lock.unlock() }
        return (accessToken, refreshToken, userId)
    }

    private func authHeaders(authenticated: Bool) -> [String: String] {
        lock.lock()
        defer { lock.unlock() }
        var h: [String: String] = [:]
        if authenticated, let t = accessToken, !t.isEmpty {
            h["Authorization"] = "Bearer \(t)"
        }
        if authenticated, let u = userId, !u.isEmpty {
            h["X-User-Id"] = u
        }
        return h
    }

    private func buildURL(path: String, query: [String: String]) throws -> URL {
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        var root = baseRoot.absoluteString
        if root.hasSuffix("/") == false { root += "/" }
        guard var comp = URLComponents(string: root + trimmed) else { throw FitnessAPIError.invalidURL }
        if !query.isEmpty {
            comp.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = comp.url else { throw FitnessAPIError.invalidURL }
        return url
    }

    private func refreshTokensLocked() async throws {
        let refresh: String?
        lock.lock()
        refresh = refreshToken
        lock.unlock()

        guard let r = refresh, !r.isEmpty else { throw FitnessAPIError.refreshFailed }

        let url = try buildURL(path: "auth/refresh", query: [:])
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("Bearer \(r)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await session.data(for: req)
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0
        guard (200...299).contains(code) else { throw FitnessAPIError.refreshFailed }

        let auth = try decoder.decode(AuthResponse.self, from: data)
        lock.lock()
        accessToken = auth.token
        refreshToken = auth.refreshToken
        userId = auth.user.id
        lock.unlock()
    }

    private func data(
        method: String,
        path: String,
        query: [String: String] = [:],
        body: Data? = nil,
        contentType: String? = "application/json",
        authenticated: Bool = true,
        allowRefreshRetry: Bool = true
    ) async throws -> Data {
        try await performData(
            method: method,
            path: path,
            query: query,
            body: body,
            contentType: contentType,
            authenticated: authenticated,
            allowRefreshRetry: allowRefreshRetry
        )
    }

    private func performData(
        method: String,
        path: String,
        query: [String: String],
        body: Data?,
        contentType: String?,
        authenticated: Bool,
        allowRefreshRetry: Bool
    ) async throws -> Data {
        let url = try buildURL(path: path, query: query)
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let b = body, !b.isEmpty {
            if let ct = contentType { req.setValue(ct, forHTTPHeaderField: "Content-Type") }
            req.httpBody = b
        } else {
            req.httpBody = nil
        }
        for (k, v) in authHeaders(authenticated: authenticated) {
            req.setValue(v, forHTTPHeaderField: k)
        }

        let (data, response) = try await session.data(for: req)
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0

        if code == 401, allowRefreshRetry, authenticated {
            do {
                try await refreshTokensLocked()
                return try await performData(
                    method: method,
                    path: path,
                    query: query,
                    body: body,
                    contentType: contentType,
                    authenticated: authenticated,
                    allowRefreshRetry: false
                )
            } catch {
                throw FitnessAPIError.refreshFailed
            }
        }

        guard (200...299).contains(code) else {
            let text = String(data: data, encoding: .utf8)
            throw FitnessAPIError.http(code, text)
        }
        return data
    }

    // MARK: - Auth

    func login(email: String, password: String) async throws -> AuthResponse {
        let body = try encoder.encode(LoginRequest(email: email, password: password))
        let data = try await self.data(method: "POST", path: "auth/login", body: body, authenticated: false)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    func register(payload: RegisterRequest) async throws -> AuthResponse {
        let body = try encoder.encode(payload)
        let data = try await self.data(method: "POST", path: "auth/register", body: body, authenticated: false)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    func logout() async {
        _ = try? await data(method: "POST", path: "auth/logout", body: nil, authenticated: true)
    }

    /// PKCE: получить URL авторизации Сбер ID (подписанный state/nonce на сервере).
    func fetchSberAuthorizeURL(codeChallenge: String, redirectURI: String) async throws -> URL {
        struct Body: Encodable {
            let codeChallenge: String
            let codeChallengeMethod: String
            let redirectUri: String
        }
        struct Response: Decodable {
            let authorizeUrl: String
        }
        let body = try encoder.encode(Body(codeChallenge: codeChallenge, codeChallengeMethod: "S256", redirectUri: redirectURI))
        let data = try await self.data(method: "POST", path: "auth/sber/login", body: body, authenticated: false)
        let parsed = try decoder.decode(Response.self, from: data)
        guard let url = URL(string: parsed.authorizeUrl) else { throw FitnessAPIError.invalidURL }
        return url
    }

    /// Обмен code + verifier на токены приложения; при привязке к текущей сессии передайте `authenticated: true`.
    func exchangeSberAuthorizationCode(
        code: String,
        codeVerifier: String,
        redirectURI: String,
        state: String,
        authenticated: Bool
    ) async throws -> AuthResponse {
        struct Body: Encodable {
            let code: String
            let codeVerifier: String
            let redirectUri: String
            let state: String
        }
        let body = try encoder.encode(Body(code: code, codeVerifier: codeVerifier, redirectUri: redirectURI, state: state))
        let data = try await self.data(method: "POST", path: "auth/sber/callback", body: body, authenticated: authenticated)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    // MARK: - User

    func getProfile() async throws -> User {
        let data = try await self.data(method: "GET", path: "user/profile")
        return try decoder.decode(User.self, from: data)
    }

    func updateProfile(_ user: User) async throws -> User {
        let body = try encoder.encode(user)
        return try decoder.decode(User.self, from: try await self.data(method: "PUT", path: "user/profile", body: body))
    }

    func getUserStats() async throws -> UserStats {
        try decoder.decode(UserStats.self, from: try await self.data(method: "GET", path: "user/stats"))
    }

    func getPurchases() async throws -> [PurchaseItem] {
        try decoder.decode([PurchaseItem].self, from: try await self.data(method: "GET", path: "user/purchases"))
    }

    func registerPushToken(_ req: PushTokenRequest) async throws {
        let body = try encoder.encode(req)
        _ = try await self.data(method: "POST", path: "user/push-token", body: body)
    }

    // MARK: - Trainings / bookings

    func getTrainings(date: String? = nil, type: String? = nil) async throws -> [Training] {
        var q: [String: String] = [:]
        if let date { q["date"] = date }
        if let type { q["type"] = type }
        return try decoder.decode([Training].self, from: try await self.data(method: "GET", path: "trainings", query: q))
    }

    func getTraining(id: String) async throws -> Training {
        try decoder.decode(Training.self, from: try await self.data(method: "GET", path: "trainings/\(id)"))
    }

    func getMyBookings() async throws -> [Booking] {
        try decoder.decode([Booking].self, from: try await self.data(method: "GET", path: "bookings"))
    }

    func bookTraining(id: String) async throws -> Booking {
        let data = try await self.data(method: "POST", path: "trainings/\(id)/book", body: nil)
        return try decoder.decode(Booking.self, from: data)
    }

    func cancelBooking(id: String) async throws {
        _ = try await self.data(method: "DELETE", path: "bookings/\(id)")
    }

    func joinWaitingList(trainingId: String) async throws -> Booking {
        let data = try await self.data(method: "POST", path: "trainings/\(trainingId)/waiting-list", body: nil)
        return try decoder.decode(Booking.self, from: data)
    }

    // MARK: - Subscriptions

    func getMySubscriptions() async throws -> [Subscription] {
        try decoder.decode([Subscription].self, from: try await self.data(method: "GET", path: "subscriptions"))
    }

    func getSubscriptionPlans() async throws -> [SubscriptionPlan] {
        try decoder.decode([SubscriptionPlan].self, from: try await self.data(method: "GET", path: "subscriptions/plans"))
    }

    func purchaseSubscription(planId: String, promoCode: String?) async throws -> Data {
        let body = try encoder.encode(PurchaseSubscriptionRequest(planId: planId, promoCode: promoCode))
        return try await self.data(method: "POST", path: "subscriptions/purchase", body: body)
    }

    func freezeSubscription(id: String, days: Int) async throws -> Subscription {
        try decoder.decode(
            Subscription.self,
            from: try await self.data(method: "POST", path: "subscriptions/\(id)/freeze", query: ["days": "\(days)"])
        )
    }

    func unfreezeSubscription(id: String) async throws -> Subscription {
        try decoder.decode(Subscription.self, from: try await self.data(method: "POST", path: "subscriptions/\(id)/unfreeze"))
    }

    // MARK: - Trainers

    func getTrainers() async throws -> [Trainer] {
        try decoder.decode([Trainer].self, from: try await self.data(method: "GET", path: "trainers"))
    }

    func getTrainer(id: String) async throws -> Trainer {
        try decoder.decode(Trainer.self, from: try await self.data(method: "GET", path: "trainers/\(id)"))
    }

    // MARK: - Products

    func getProducts() async throws -> [Product] {
        try decoder.decode([Product].self, from: try await self.data(method: "GET", path: "products"))
    }

    func purchaseProduct(id: String, body: PurchaseProductRequest?) async throws -> PurchaseProductResponse {
        let data: Data
        if let body {
            data = try await self.data(method: "POST", path: "products/\(id)/purchase", body: try encoder.encode(body))
        } else {
            data = try await self.data(method: "POST", path: "products/\(id)/purchase", body: nil)
        }
        return try decoder.decode(PurchaseProductResponse.self, from: data)
    }

    // MARK: - Club

    func getClubInfo() async throws -> ClubInfo {
        try decoder.decode(ClubInfo.self, from: try await self.data(method: "GET", path: "club/info"))
    }

    func getClubPromotions() async throws -> [ClubPromotion] {
        try decoder.decode([ClubPromotion].self, from: try await self.data(method: "GET", path: "club/promotions"))
    }

    func getClubs() async throws -> [ClubItem] {
        try decoder.decode([ClubItem].self, from: try await self.data(method: "GET", path: "clubs"))
    }

    func getClubDetails(id: String) async throws -> ClubInfo {
        try decoder.decode(ClubInfo.self, from: try await self.data(method: "GET", path: "clubs/\(id)"))
    }

    func getClubOccupancy() async throws -> GymOccupancy {
        try decoder.decode(GymOccupancy.self, from: try await self.data(method: "GET", path: "club/occupancy"))
    }

    // MARK: - Notifications

    func getNotifications() async throws -> [ApiNotification] {
        try decoder.decode([ApiNotification].self, from: try await self.data(method: "GET", path: "notifications"))
    }

    func markNotificationRead(id: String) async throws {
        _ = try await self.data(method: "POST", path: "notifications/\(id)/read", body: nil)
    }

    func markAllNotificationsRead() async throws {
        _ = try await self.data(method: "POST", path: "notifications/read-all", body: nil)
    }

    // MARK: - Feedback

    func submitFeedback(_ feedback: FeedbackRequest) async throws -> FeedbackResponse {
        let body = try encoder.encode(feedback)
        return try decoder.decode(FeedbackResponse.self, from: try await self.data(method: "POST", path: "feedback", body: body))
    }

    func createSupportTicket(_ ticket: SupportTicketRequest) async throws -> SupportTicketCreateResponse {
        let body = try encoder.encode(ticket)
        return try decoder.decode(SupportTicketCreateResponse.self, from: try await self.data(method: "POST", path: "support/tickets", body: body))
    }

    // MARK: - Guest passes

    func getGuestPasses() async throws -> [GuestPass] {
        try decoder.decode([GuestPass].self, from: try await self.data(method: "GET", path: "guest-passes"))
    }

    func createGuestPass(_ req: CreateGuestPassRequest) async throws -> GuestPass {
        let body = try encoder.encode(req)
        return try decoder.decode(GuestPass.self, from: try await self.data(method: "POST", path: "guest-passes", body: body))
    }

    // MARK: - Lockers

    func getLockers() async throws -> [Locker] {
        try decoder.decode([Locker].self, from: try await self.data(method: "GET", path: "lockers"))
    }

    func getMyLockerBooking() async throws -> LockerBooking? {
        let data = try await self.data(method: "GET", path: "lockers/my-booking")
        let s = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if s == "null" || s.isEmpty { return nil }
        return try decoder.decode(LockerBooking.self, from: data)
    }

    func bookLocker(id: String) async throws -> LockerBooking {
        try decoder.decode(LockerBooking.self, from: try await self.data(method: "POST", path: "lockers/\(id)/book", body: nil))
    }

    func releaseLocker() async throws {
        _ = try await self.data(method: "POST", path: "lockers/release", body: nil)
    }

    // MARK: - Documents

    func getDocuments() async throws -> [ApiDocument] {
        try decoder.decode([ApiDocument].self, from: try await self.data(method: "GET", path: "documents"))
    }

    func uploadDocument(fileData: Data, fileName: String, mime: String, displayName: String, category: String?, didRefresh: Bool = false) async throws -> ApiDocument {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()

        func append(_ str: String) {
            body.append(Data(str.utf8))
        }

        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n")
        append("Content-Type: \(mime)\r\n\r\n")
        body.append(fileData)
        append("\r\n")

        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"name\"\r\n\r\n")
        append("\(displayName)\r\n")

        if let category, !category.isEmpty {
            append("--\(boundary)\r\n")
            append("Content-Disposition: form-data; name=\"category\"\r\n\r\n")
            append("\(category)\r\n")
        }

        append("--\(boundary)--\r\n")

        let url = try buildURL(path: "documents", query: [:])
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        for (k, v) in authHeaders(authenticated: true) {
            req.setValue(v, forHTTPHeaderField: k)
        }
        req.httpBody = body

        let (data, response) = try await session.data(for: req)
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0

        if code == 401, !didRefresh {
            try await refreshTokensLocked()
            return try await uploadDocument(
                fileData: fileData,
                fileName: fileName,
                mime: mime,
                displayName: displayName,
                category: category,
                didRefresh: true
            )
        }

        guard (200...299).contains(code) else {
            throw FitnessAPIError.http(code, String(data: data, encoding: .utf8))
        }
        return try decoder.decode(ApiDocument.self, from: data)
    }

    func downloadDocument(id: String) async throws -> Data {
        try await self.data(method: "GET", path: "documents/\(id)/download", contentType: nil, authenticated: true)
    }
}

// MARK: - Покупка абонемента (403 + Сбер ID)

enum PurchaseSubscriptionOutcome: Sendable {
    case success(Subscription)
    case verificationRequired(authorizeURL: URL, message: String)
    case error(String)
}

extension FitnessAPI {
    func purchaseSubscriptionParsed(planId: String, promoCode: String?) async -> PurchaseSubscriptionOutcome {
        do {
            let data = try await purchaseSubscription(planId: planId, promoCode: promoCode)
            if let sub = try? decoder.decode(Subscription.self, from: data) {
                return .success(sub)
            }
            return .error("Некорректный ответ сервера")
        } catch let e as FitnessAPIError {
            if case .http(let code, let raw) = e, code == 403, let raw, let bodyData = raw.data(using: .utf8),
               let parsed = try? decoder.decode(SubscriptionPurchaseErrorBody.self, from: bodyData),
               parsed.code == "verification_required",
               let urlStr = parsed.authorizeUrl,
               let url = URL(string: urlStr)
            {
                let msg = parsed.message
                    ?? "Требуется верификация через Сбер ID. После успеха вернитесь и нажмите «Купить» снова."
                return .verificationRequired(authorizeURL: url, message: msg)
            }
            return .error(e.localizedDescription)
        } catch {
            return .error(error.localizedDescription)
        }
    }
}
