import Foundation

/// Сохраняет POST и тело при HTTP-редиректах (301/302 с `http` на `https` иначе может превратить запрос в GET без тела → `missing_code_challenge`).
private final class FitnessURLSessionDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let original = task.originalRequest,
              original.httpMethod == "POST",
              let body = original.httpBody,
              !body.isEmpty
        else {
            completionHandler(request)
            return
        }
        var next = request
        next.httpMethod = "POST"
        next.httpBody = body
        if let v = original.value(forHTTPHeaderField: "Content-Type") {
            next.setValue(v, forHTTPHeaderField: "Content-Type")
        }
        if let v = original.value(forHTTPHeaderField: "Accept") {
            next.setValue(v, forHTTPHeaderField: "Accept")
        }
        completionHandler(next)
    }
}

enum FitnessAPIError: Error, LocalizedError {
    case invalidURL
    case http(Int, String?)
    case decoding(Error)
    case emptyBody
    /// Refresh отклонён сервером (401/403) — нужен повторный вход.
    case sessionExpired
    /// Временный сбой обновления (сеть/5xx) — сессию не сбрасываем.
    case refreshTemporarilyUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Некорректный URL"
        case .http(let code, let body):
            return Self.userMessage(from: body, httpCode: code)
        case .decoding: return "Не удалось обработать ответ сервера"
        case .emptyBody: return "Пустой ответ сервера"
        case .sessionExpired: return "Сессия истекла. Войдите снова."
        case .refreshTemporarilyUnavailable:
            return "Не удалось обновить сессию. Проверьте интернет и попробуйте ещё раз."
        }
    }

    /// Устаревший алиас — часть экранов могла ловить `.refreshFailed`.
    static var refreshFailed: FitnessAPIError { .sessionExpired }

    static func userMessage(from body: String?, httpCode: Int? = nil) -> String {
        if let body, !body.isEmpty {
            if let data = body.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            {
                let error = (json["error"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let message = (json["message"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let code = (json["code"] as? String) ?? ""
                let server = !error.isEmpty ? error : message
                return mapMessage(error: server, code: code)
            }
            return mapMessage(error: body, code: "")
        }
        if let httpCode {
            return "Не удалось выполнить запрос (код \(httpCode))"
        }
        return "Не удалось выполнить запрос"
    }

    /// Коды, при которых Android сбрасывает сессию (`isSessionRejected`).
    static func isSessionRejectedAuthCode(_ code: String?) -> Bool {
        switch code?.trimmingCharacters(in: .whitespacesAndNewlines) {
        case "invalid_refresh", "token_expired", "invalid_token", "user_blocked":
            return true
        default:
            return false
        }
    }

    static func authCode(from body: String?) -> String? {
        guard let body, let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let code = json["code"] as? String
        else { return nil }
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Код ошибки из тела ответа API (`code`), если есть.
    var apiCode: String? {
        guard case .http(_, let body) = self, let body, let data = body.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let code = json["code"] as? String, !code.isEmpty
        else { return nil }
        return code
    }

    private static func mapMessage(error: String, code: String) -> String {
        switch code {
        case "invalid_credentials":
            return "Неверный email или пароль"
        case "password_not_set":
            return "Аккаунт с этим email уже есть в клубе, но пароль ещё не задан.\n\nПройдите регистрацию с этим email и придумайте пароль — вход откроется сразу."
        case "email_unknown":
            return "Аккаунт не найден. Зарегистрируйтесь."
        case "password_required", "missing_password":
            return "Введите пароль"
        case "missing_email":
            return "Укажите email"
        case "user_blocked":
            return "Доступ запрещён"
        case "missing_credentials":
            return "Введите email и пароль"
        case "invalid_email":
            return "Некорректный email"
        case "weak_password":
            return "Пароль должен быть не менее 6 символов"
        case "email_already_exists":
            return "Этот email уже зарегистрирован. Войдите в аккаунт или укажите другой адрес."
        case "invalid_current_password":
            return "Неверный текущий пароль"
        case "same_password":
            return "Новый пароль должен отличаться от текущего"
        case "passport_locked", "profile_locked":
            return "На ваши данные приобретён активный абонемент. Если данные изменились, свяжитесь со службой поддержки."
        case "channel_unavailable":
            return error.isEmpty
                ? "Отправка SMS сейчас недоступна. Войдите по почте или попробуйте позже."
                : error
        case "channel_undeliverable":
            return error.isEmpty
                ? "Не удалось доставить SMS на этот номер. Проверьте номер или войдите по почте."
                : error
        case "channel_failed":
            return error.isEmpty
                ? "Не удалось отправить SMS с кодом. Попробуйте позже или войдите по почте."
                : error
        case "otp_rate_limited":
            return "Слишком много запросов кода. Подождите час."
        case "otp_too_soon":
            return "Повторная отправка будет доступна через несколько секунд"
        case "otp_not_found":
            return "Сначала запросите код"
        case "otp_expired":
            return "Код устарел, запросите новый"
        case "otp_locked":
            return "Слишком много попыток. Запросите новый код"
        case "invalid_phone":
            return "Укажите номер телефона полностью"
        case "invalid_channel":
            return "Не удалось отправить код на телефон"
        case "invalid_otp", "invalid_code":
            return error.isEmpty ? "Неверный код" : error
        case "invalid_ticket", "ticket_expired":
            return error.isEmpty
                ? "Сессия регистрации устарела, подтвердите номер снова"
                : error
        default:
            break
        }

        let text = error.trimmingCharacters(in: .whitespacesAndNewlines)
        switch text {
        case "User not found":
            return "Неверный email или пароль"
        case "Access denied":
            return "Доступ запрещён"
        case "Укажите email и password":
            return "Введите email и пароль"
        case "Для этого аккаунта вход по паролю не настроен":
            return "Аккаунт с этим email уже есть в клубе, но пароль ещё не задан.\n\nПройдите регистрацию с этим email и придумайте пароль — вход откроется сразу."
        case "Пользователь с таким email уже зарегистрирован",
             "Пользователь с таким email уже существует",
             "User with this email already exists":
            return "Этот email уже зарегистрирован. Войдите в аккаунт или укажите другой адрес."
        case "Укажите email", "Email is required":
            return "Укажите email"
        case "Некорректный email":
            return "Некорректный email"
        case "Пароль должен быть не менее 6 символов":
            return "Пароль должен быть не менее 6 символов"
        default:
            if text.contains("{") || text.contains("code") {
                return "Не удалось выполнить запрос. Попробуйте позже."
            }
            if text.isEmpty {
                return "Не удалось выполнить запрос"
            }
            return text
        }
    }
}

/// Низкоуровневый HTTP-клиент: заголовки как в Android (`Bearer` + `X-User-Id`), при 401 — одна попытка `auth/refresh`.
final class FitnessAPI: @unchecked Sendable {
    private let baseRoot: URL
    private let session: URLSession
    private let urlSessionDelegate = FitnessURLSessionDelegate()
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder

    private let lock = NSLock()
    private var accessToken: String?
    private var refreshToken: String?
    private var userId: String?

    /// Single-flight обновление access (как `synchronized` в `TokenRefreshAuthenticator`).
    private var refreshTask: Task<AuthResponse, Error>?
    private var refreshGeneration = 0

    /// Успешный refresh — сохранить пару в Keychain (см. `WorldFitnessAppState`).
    var onSessionRefreshed: ((AuthResponse) -> Void)?
    /// Refresh отклонён сервером — локальный logout.
    var onSessionInvalidated: (() -> Void)?

    /// Короткий кэш GET — чтобы повторный заход на экран не ждал сеть снова.
    private var getCache: [String: (data: Data, at: Date)] = [:]
    private let getCacheTTL: TimeInterval = 45

    init() {
        guard let u = Self.normalizedAPIRootURL(from: AppConfiguration.apiBaseURLString) else {
            fatalError("Invalid AppConfiguration.apiBaseURLString")
        }
        baseRoot = u
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        session = URLSession(configuration: config, delegate: urlSessionDelegate, delegateQueue: nil)
        decoder = AppJSON.decoder()
        encoder = AppJSON.encoder()
    }

    /// Без редиректа http→https: иначе POST с JSON часто теряет тело (см. `FitnessURLSessionDelegate`).
    private static func normalizedAPIRootURL(from raw: String) -> URL? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("http://") {
            let host = URL(string: s)?.host?.lowercased() ?? ""
            if host == "worldcashfit.ru" || host == "www.worldcashfit.ru" {
                s = "https://" + s.dropFirst("http://".count)
            }
        }
        return URL(string: s)
    }

    func setCredentials(access: String?, refresh: String?, userId: String?) {
        lock.lock()
        defer { lock.unlock() }
        accessToken = access
        refreshToken = refresh
        self.userId = userId
        if access == nil {
            getCache.removeAll()
        }
    }

    func invalidateGetCache(pathPrefix: String? = nil) {
        lock.lock()
        defer { lock.unlock() }
        guard let pathPrefix, !pathPrefix.isEmpty else {
            getCache.removeAll()
            return
        }
        getCache = getCache.filter { !$0.key.hasPrefix(pathPrefix) }
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
        let task: Task<AuthResponse, Error>
        let generation: Int
        lock.lock()
        if let existing = refreshTask {
            lock.unlock()
            _ = try await existing.value
            return
        }
        refreshGeneration += 1
        generation = refreshGeneration
        task = Task<AuthResponse, Error> {
            try await self.performTokenRefresh()
        }
        refreshTask = task
        lock.unlock()

        defer {
            lock.lock()
            if refreshGeneration == generation {
                refreshTask = nil
            }
            lock.unlock()
        }
        _ = try await task.value
    }

    /// Как Android `TokenRefreshAuthenticator.refreshAccess` + persist.
    private func performTokenRefresh() async throws -> AuthResponse {
        let refresh: String?
        lock.lock()
        refresh = refreshToken
        lock.unlock()

        guard let r = refresh?.trimmingCharacters(in: .whitespacesAndNewlines), !r.isEmpty else {
            onSessionInvalidated?()
            throw FitnessAPIError.sessionExpired
        }

        do {
            let url = try buildURL(path: "auth/refresh", query: [:])
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Accept")
            req.setValue("Bearer \(r)", forHTTPHeaderField: "Authorization")

            let (data, response) = try await session.data(for: req)
            let http = response as? HTTPURLResponse
            let code = http?.statusCode ?? 0
            let bodyText = String(data: data, encoding: .utf8)
            let authCode = FitnessAPIError.authCode(from: bodyText)

            if code == 401 || code == 403 || FitnessAPIError.isSessionRejectedAuthCode(authCode) {
                onSessionInvalidated?()
                throw FitnessAPIError.sessionExpired
            }
            guard (200...299).contains(code) else {
                // 5xx / странный ответ — сессию не трогаем (как Android invalidateSession=false).
                throw FitnessAPIError.refreshTemporarilyUnavailable
            }

            let auth = try decoder.decode(AuthResponse.self, from: data)
            guard !auth.token.isEmpty, !auth.refreshToken.isEmpty else {
                throw FitnessAPIError.refreshTemporarilyUnavailable
            }

            lock.lock()
            accessToken = auth.token
            refreshToken = auth.refreshToken
            userId = auth.user.id
            getCache.removeAll()
            lock.unlock()

            onSessionRefreshed?(auth)
            return auth
        } catch let e as FitnessAPIError {
            throw e
        } catch is URLError {
            throw FitnessAPIError.refreshTemporarilyUnavailable
        } catch {
            // Декодирование / прочее — не сбрасываем сессию из-за одного битого ответа.
            throw FitnessAPIError.refreshTemporarilyUnavailable
        }
    }

    /// Проактивный refresh при старте (Android `bootstrapSession`).
    func bootstrapRefresh() async {
        lock.lock()
        let hasRefresh = !(refreshToken ?? "").isEmpty
        let hadAccess = !(accessToken ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        lock.unlock()
        guard hasRefresh else { return }
        do {
            try await refreshTokensLocked()
        } catch FitnessAPIError.sessionExpired {
            // onSessionInvalidated уже вызван
        } catch {
            // Офлайн / 5xx: как Android — если access уже с диска, остаёмся; иначе сброс.
            if !hadAccess {
                onSessionInvalidated?()
            }
        }
    }

    private func data(
        method: String,
        path: String,
        query: [String: String] = [:],
        body: Data? = nil,
        contentType: String? = "application/json",
        authenticated: Bool = true,
        allowRefreshRetry: Bool = true,
        useCache: Bool = true
    ) async throws -> Data {
        try await performData(
            method: method,
            path: path,
            query: query,
            body: body,
            contentType: contentType,
            authenticated: authenticated,
            allowRefreshRetry: allowRefreshRetry,
            useCache: useCache
        )
    }

    private func cacheKey(path: String, query: [String: String]) -> String {
        let q = query.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        return q.isEmpty ? path : "\(path)?\(q)"
    }

    private func cachedGet(path: String, query: [String: String]) -> Data? {
        let key = cacheKey(path: path, query: query)
        lock.lock()
        defer { lock.unlock() }
        guard let entry = getCache[key] else { return nil }
        if Date().timeIntervalSince(entry.at) > getCacheTTL {
            getCache.removeValue(forKey: key)
            return nil
        }
        return entry.data
    }

    private func storeGetCache(path: String, query: [String: String], data: Data) {
        let key = cacheKey(path: path, query: query)
        lock.lock()
        getCache[key] = (data, Date())
        lock.unlock()
    }

    private func performData(
        method: String,
        path: String,
        query: [String: String],
        body: Data?,
        contentType: String?,
        authenticated: Bool,
        allowRefreshRetry: Bool,
        useCache: Bool
    ) async throws -> Data {
        let methodUpper = method.uppercased()
        if methodUpper == "GET", useCache, let cached = cachedGet(path: path, query: query) {
            return cached
        }

        let url = try buildURL(path: path, query: query)
        var req = URLRequest(url: url)
        req.httpMethod = method
        // При forceRefresh обходим и наш in-memory кэш, и URLCache системы —
        // иначе pull-to-refresh в профиле показывает старые посещения до перезапуска приложения.
        if methodUpper == "GET", !useCache {
            req.cachePolicy = .reloadIgnoringLocalCacheData
            req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
            req.setValue("no-cache", forHTTPHeaderField: "Pragma")
        }
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
            let accessBefore: String?
            lock.lock()
            accessBefore = accessToken
            lock.unlock()
            do {
                try await refreshTokensLocked()
                return try await performData(
                    method: method,
                    path: path,
                    query: query,
                    body: body,
                    contentType: contentType,
                    authenticated: authenticated,
                    allowRefreshRetry: false,
                    useCache: false
                )
            } catch FitnessAPIError.sessionExpired {
                throw FitnessAPIError.sessionExpired
            } catch FitnessAPIError.refreshTemporarilyUnavailable {
                // Как Android: refresh временно недоступен — отдаём исходный 401, сессию не сбрасываем.
                let text = String(data: data, encoding: .utf8)
                throw FitnessAPIError.http(code, text)
            } catch {
                // Параллельный запрос уже обновил access — повторим с новым токеном.
                lock.lock()
                let accessAfter = accessToken
                lock.unlock()
                if let accessAfter, !accessAfter.isEmpty, accessAfter != accessBefore {
                    return try await performData(
                        method: method,
                        path: path,
                        query: query,
                        body: body,
                        contentType: contentType,
                        authenticated: authenticated,
                        allowRefreshRetry: false,
                        useCache: false
                    )
                }
                let text = String(data: data, encoding: .utf8)
                throw FitnessAPIError.http(code, text)
            }
        }

        guard (200...299).contains(code) else {
            let text = String(data: data, encoding: .utf8)
            throw FitnessAPIError.http(code, text)
        }

        if methodUpper == "GET" {
            storeGetCache(path: path, query: query, data: data)
        } else {
            // Любая мутация — сбрасываем кэш, чтобы экраны не показывали устаревшее.
            invalidateGetCache()
        }
        return data
    }

    // MARK: - Auth

    func login(email: String, password: String) async throws -> AuthResponse {
        let body = try encoder.encode(LoginRequest(email: email, password: password))
        let data = try await self.data(method: "POST", path: "auth/login", body: body, authenticated: false)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    func loginHint(email: String) async throws -> LoginHintResponse {
        let body = try encoder.encode(LoginHintRequest(email: email))
        let data = try await self.data(method: "POST", path: "auth/login-hint", body: body, authenticated: false)
        return try decoder.decode(LoginHintResponse.self, from: data)
    }

    func changePassword(currentPassword: String, newPassword: String) async throws {
        let body = try encoder.encode(ChangePasswordRequest(currentPassword: currentPassword, newPassword: newPassword))
        _ = try await self.data(method: "POST", path: "user/change-password", body: body)
    }

    func getNotificationSettings() async throws -> NotificationSettings {
        try decoder.decode(NotificationSettings.self, from: try await self.data(method: "GET", path: "user/notification-settings"))
    }

    func updateNotificationSettings(_ settings: NotificationSettings) async throws -> NotificationSettings {
        let body = try encoder.encode(settings)
        return try decoder.decode(
            NotificationSettings.self,
            from: try await self.data(method: "PUT", path: "user/notification-settings", body: body)
        )
    }

    func register(payload: RegisterRequest) async throws -> AuthResponse {
        let body = try encoder.encode(payload)
        let data = try await self.data(method: "POST", path: "auth/register", body: body, authenticated: false)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    func registerPhone(payload: RegisterRequest) async throws -> AuthResponse {
        let body = try encoder.encode(payload)
        let data = try await self.data(method: "POST", path: "auth/register/phone", body: body, authenticated: false)
        return try decoder.decode(AuthResponse.self, from: data)
    }

    func checkRegisterEmail(_ email: String) async throws -> CheckEmailResponse {
        let body = try encoder.encode(CheckEmailRequest(email: email))
        let data = try await self.data(method: "POST", path: "auth/register/check-email", body: body, authenticated: false)
        return try decoder.decode(CheckEmailResponse.self, from: data)
    }

    func otpChannels() async throws -> [OtpChannelStatus] {
        let data = try await self.data(method: "GET", path: "auth/otp/channels", authenticated: false)
        return try decoder.decode(OtpChannelsResponse.self, from: data).channels
    }

    func requestOtp(phone: String, channel: String) async throws -> OtpRequestResponse {
        let body = try encoder.encode(OtpRequestBody(phone: phone, channel: channel))
        let data = try await self.data(method: "POST", path: "auth/otp/request", body: body, authenticated: false)
        return try decoder.decode(OtpRequestResponse.self, from: data)
    }

    func verifyOtp(phone: String, code: String) async throws -> OtpVerifyResponse {
        let body = try encoder.encode(OtpVerifyBody(phone: phone, code: code))
        let data = try await self.data(method: "POST", path: "auth/otp/verify", body: body, authenticated: false)
        if let parsed = try? decoder.decode(OtpVerifyResponse.self, from: data) {
            return parsed
        }
        // Ответ успешного входа = AuthResponse (token/refresh_token/user).
        let auth = try decoder.decode(AuthResponse.self, from: data)
        return OtpVerifyResponse(
            token: auth.token,
            refreshToken: auth.refreshToken,
            user: auth.user,
            registrationRequired: false,
            otpTicket: nil,
            phone: auth.user.phone
        )
    }

    func resendEmailVerification() async throws -> EmailResendResponse {
        let data = try await self.data(method: "POST", path: "user/email/resend", body: nil)
        return try decoder.decode(EmailResendResponse.self, from: data)
    }

    /// Сохранить паспорт перед покупкой (как `AuthRepository.savePassportForPurchase`).
    func savePassportForPurchase(
        current: User,
        name: String,
        email: String,
        dateOfBirth: String?,
        series: String,
        number: String,
        issuedBy: String,
        issueDate: String,
        registrationAddress: String
    ) async throws -> User {
        let updated = current.withPassportForPurchase(
            name: name,
            email: email,
            dateOfBirth: dateOfBirth,
            series: series,
            number: number,
            issuedBy: issuedBy,
            issueDate: issueDate,
            registrationAddress: registrationAddress
        )
        return try await updateProfile(updated)
    }

    func logout() async {
        _ = try? await data(method: "POST", path: "auth/logout", body: nil, authenticated: true)
    }

    /// Восстановление сессии по refresh (биометрический вход, как на Android).
    func restoreSessionWithRefreshToken(_ refresh: String) async throws -> AuthResponse {
        lock.lock()
        refreshToken = refresh
        lock.unlock()
        return try await performTokenRefresh()
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

    /// Привязка клуба после выбора зала при регистрации (`club_id` в CRM).
    func assignClub(clubId: String) async throws -> User {
        struct Body: Encodable {
            let clubId: String
            enum CodingKeys: String, CodingKey {
                case clubId = "club_id"
            }
        }
        let body = try encoder.encode(Body(clubId: clubId))
        return try decoder.decode(User.self, from: try await self.data(method: "PUT", path: "user/profile", body: body))
    }

    /// Безвозвратное удаление аккаунта и обезличивание персональных данных на сервере.
    func deleteAccount() async throws {
        _ = try await self.data(method: "DELETE", path: "user/account")
    }

    func getUserStats(forceRefresh: Bool = false) async throws -> UserStats {
        try decoder.decode(
            UserStats.self,
            from: try await self.data(method: "GET", path: "user/stats", useCache: !forceRefresh)
        )
    }

    func getPurchases() async throws -> [PurchaseItem] {
        try decoder.decode([PurchaseItem].self, from: try await self.data(method: "GET", path: "user/purchases"))
    }

    func registerPushToken(_ req: PushTokenRequest) async throws {
        let body = try encoder.encode(req)
        _ = try await self.data(method: "POST", path: "user/push-token", body: body)
    }

    func unregisterPushToken() async throws {
        _ = try await self.data(method: "DELETE", path: "user/push-token")
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

    func getMyBookings(upcoming: Bool? = nil, forceRefresh: Bool = false) async throws -> [Booking] {
        var q: [String: String] = [:]
        if let upcoming {
            q["upcoming"] = upcoming ? "true" : "false"
        }
        return try decoder.decode(
            [Booking].self,
            from: try await self.data(method: "GET", path: "bookings", query: q, useCache: !forceRefresh)
        )
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

    func getMySubscriptions(forceRefresh: Bool = false) async throws -> [Subscription] {
        try decoder.decode(
            [Subscription].self,
            from: try await self.data(method: "GET", path: "subscriptions", useCache: !forceRefresh)
        )
    }

    func getSubscriptionPlans() async throws -> [SubscriptionPlan] {
        try decoder.decode([SubscriptionPlan].self, from: try await self.data(method: "GET", path: "subscriptions/plans"))
    }

    func purchaseSubscription(planId: String, promoCode: String?, clubId: String? = nil) async throws -> Data {
        let body = try encoder.encode(PurchaseSubscriptionRequest(planId: planId, promoCode: promoCode, clubId: clubId))
        return try await self.data(method: "POST", path: "subscriptions/purchase", body: body)
    }

    func validatePromoCode(_ code: String) async throws -> PromoValidationResponse {
        let body = try encoder.encode(PromoCodeRequest(promoCode: code))
        return try decoder.decode(
            PromoValidationResponse.self,
            from: try await self.data(method: "POST", path: "payments/promo/validate", body: body)
        )
    }

    func quoteSubscriptionPayment(planId: String, promoCode: String?, clubId: String? = nil) async throws -> SubscriptionPaymentQuoteResponse {
        let body = try encoder.encode(PurchaseSubscriptionRequest(planId: planId, promoCode: promoCode, clubId: clubId))
        return try decoder.decode(
            SubscriptionPaymentQuoteResponse.self,
            from: try await self.data(method: "POST", path: "payments/subscription/quote", body: body)
        )
    }

    func initSubscriptionPayment(planId: String, promoCode: String?, clubId: String? = nil) async throws -> SubscriptionPaymentInitResponse {
        let body = try encoder.encode(PurchaseSubscriptionRequest(planId: planId, promoCode: promoCode, clubId: clubId))
        return try decoder.decode(
            SubscriptionPaymentInitResponse.self,
            from: try await self.data(method: "POST", path: "payments/subscription/init", body: body)
        )
    }

    func getPaymentStatus(paymentId: Int) async throws -> SubscriptionPaymentInitResponse {
        try decoder.decode(
            SubscriptionPaymentInitResponse.self,
            from: try await self.data(method: "GET", path: "payments/\(paymentId)/status")
        )
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

    func cancelSubscription(id: String) async throws -> Subscription {
        try decoder.decode(Subscription.self, from: try await self.data(method: "POST", path: "subscriptions/\(id)/cancel"))
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

    func getClubPromotions(forceRefresh: Bool = false) async throws -> [ClubPromotion] {
        try decoder.decode(
            [ClubPromotion].self,
            from: try await self.data(method: "GET", path: "club/promotions", useCache: !forceRefresh)
        )
    }

    func getClubs() async throws -> [ClubItem] {
        try decoder.decode([ClubItem].self, from: try await self.data(method: "GET", path: "clubs"))
    }

    func getClubDetails(id: String) async throws -> ClubInfo {
        try decoder.decode(ClubInfo.self, from: try await self.data(method: "GET", path: "clubs/\(id)"))
    }

    func getClubOccupancy(clubId: String? = nil, forceRefresh: Bool = true) async throws -> GymOccupancy {
        var path = "club/occupancy"
        if let clubId, !clubId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            let encoded = clubId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? clubId
            path += "?club_id=\(encoded)"
        }
        return try decoder.decode(
            GymOccupancy.self,
            from: try await self.data(method: "GET", path: path, useCache: !forceRefresh)
        )
    }

    func getAccessStatus(forceRefresh: Bool = false) async throws -> AccessStatus {
        try decoder.decode(
            AccessStatus.self,
            from: try await self.data(method: "GET", path: "user/access-status", useCache: !forceRefresh)
        )
    }

    // MARK: - Legal documents

    func getLegalDocument(slug: String) async throws -> LegalDocumentResponse {
        try decoder.decode(LegalDocumentResponse.self, from: try await self.data(method: "GET", path: "legal/\(slug)"))
    }

    // MARK: - Notifications

    func getNotifications(forceRefresh: Bool = false) async throws -> [ApiNotification] {
        try decoder.decode(
            [ApiNotification].self,
            from: try await self.data(method: "GET", path: "notifications", useCache: !forceRefresh)
        )
    }

    func getUnreadNotificationsCount(forceRefresh: Bool = false) async throws -> Int {
        struct Body: Decodable { let unreadCount: Int }
        let body = try decoder.decode(
            Body.self,
            from: try await self.data(method: "GET", path: "notifications/unread-count", useCache: !forceRefresh)
        )
        return body.unreadCount
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
    case paymentRequired(paymentId: Int, paymentUrl: URL, amount: Double)
    case verificationRequired(authorizeURL: URL, message: String)
    case passportRequired(String)
    case emailUnverified(String)
    case error(String)
}

extension FitnessAPI {
    func purchaseSubscriptionParsed(planId: String, promoCode: String?, clubId: String? = nil) async -> PurchaseSubscriptionOutcome {
        do {
            let response = try await initSubscriptionPayment(planId: planId, promoCode: promoCode, clubId: clubId)
            if let urlStr = response.paymentUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
               !urlStr.isEmpty,
               let url = URL(string: urlStr)
            {
                let amount = response.finalPrice > 0 ? response.finalPrice : response.amount
                return .paymentRequired(paymentId: response.paymentId, paymentUrl: url, amount: amount)
            }
            return .error("Не получен URL оплаты")
        } catch let e as FitnessAPIError {
            if case .http(let code, let raw) = e {
                if let outcome = Self.parsePurchaseError(httpCode: code, raw: raw, decoder: decoder) {
                    return outcome
                }
                return .error(Self.humanizeApiError(httpCode: code, raw: raw ?? ""))
            }
            return .error(e.localizedDescription)
        } catch {
            return .error(error.localizedDescription)
        }
    }

    private static func parsePurchaseError(
        httpCode: Int,
        raw: String?,
        decoder: JSONDecoder
    ) -> PurchaseSubscriptionOutcome? {
        guard (httpCode == 403 || httpCode == 400 || httpCode == 422),
              let raw,
              let bodyData = raw.data(using: .utf8),
              let parsed = try? decoder.decode(SubscriptionPurchaseErrorBody.self, from: bodyData)
        else { return nil }

        let msg = parsed.message?.trimmingCharacters(in: .whitespacesAndNewlines)
        switch parsed.code {
        case "verification_required":
            guard let urlStr = parsed.authorizeUrl, let url = URL(string: urlStr) else { return nil }
            return .verificationRequired(
                authorizeURL: url,
                message: (msg?.isEmpty == false ? msg! : "Требуется верификация через Сбер ID. После успеха вернитесь и нажмите «Купить» снова.")
            )
        case "passport_required":
            return .passportRequired(msg?.isEmpty == false ? msg! : "Для покупки заполните паспортные данные")
        case "email_unverified":
            return .emailUnverified(msg?.isEmpty == false ? msg! : "Подтвердите email, чтобы оформить абонемент")
        default:
            return nil
        }
    }

    private static func humanizeApiError(httpCode: Int, raw: String) -> String {
        if let bodyData = raw.data(using: .utf8),
           let parsed = try? AppJSON.decoder().decode(SubscriptionPurchaseErrorBody.self, from: bodyData)
        {
            if let message = parsed.message?.trimmingCharacters(in: .whitespacesAndNewlines), !message.isEmpty {
                return message
            }
            switch parsed.code {
            case "user_blocked": return "Аккаунт заблокирован. Обратитесь в клуб."
            case "token_expired", "invalid_token": return "Сессия истекла. Выйдите и войдите в приложение снова."
            case "missing_token": return "Войдите в приложение, чтобы оплатить абонемент."
            default: break
            }
            switch parsed.error?.trimmingCharacters(in: .whitespacesAndNewlines) {
            case "Access denied": return "Аккаунт заблокирован. Обратитесь в клуб."
            case "Unauthorized": return "Сессия истекла. Выйдите и войдите в приложение снова."
            case "Forbidden": return "Нет доступа к оплате. Войдите снова или обратитесь в клуб."
            default: break
            }
        }

        if raw.localizedCaseInsensitiveContains("<!DOCTYPE") || raw.localizedCaseInsensitiveContains("<html") {
            switch httpCode {
            case 404: return "Сервис оплаты на сервере не настроен (404). Обратитесь в поддержку клуба."
            case 502, 503: return "Сервер временно недоступен. Попробуйте позже."
            default: return "Ошибка сервера (\(httpCode)). Попробуйте позже или обратитесь в клуб."
            }
        }

        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "Ошибка инициализации оплаты" : trimmed
    }
}
