import Foundation

enum StaffApiError: LocalizedError {
    case http(status: Int, detail: String)
    case emptyBody
    case htmlResponse
    case parseFailed(String)

    var errorDescription: String? {
        switch self {
        case .http(let status, let detail):
            return "HTTP \(status): \(detail)"
        case .emptyBody:
            return "Empty response body"
        case .htmlResponse:
            return "HTML response instead of JSON"
        case .parseFailed(let message):
            return "JSON parse failed: \(message)"
        }
    }
}

final class StaffApiClient {
    private let baseUrl: String
    private let session: URLSession

    init(baseUrl: String = StaffApiUrl.resolve()) {
        self.baseUrl = baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 60
        self.session = URLSession(configuration: config)
    }

    func register(email: String, name: String, password: String) async throws -> StaffSession {
        let payload: [String: Any] = [
            "email": email, "name": name, "password": password,
        ]
        return try await authRequest(path: "/api/v1/staff/auth/register", payload: payload)
    }

    func login(email: String, password: String) async throws -> StaffSession {
        let payload: [String: Any] = ["email": email, "password": password]
        return try await authRequest(path: "/api/v1/staff/auth/login", payload: payload)
    }

    func refresh(refreshToken: String) async throws -> StaffSession {
        var request = try openRequest(path: "/api/v1/staff/auth/refresh", method: "POST")
        request.setValue("Bearer \(refreshToken)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return parseSession(json)
    }

    func loadConfig(token: String) async throws -> RoleConfig {
        var request = try openRequest(path: "/api/v1/staff/config", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return RoleConfig(
            roles: json.stringList("roles"),
            appSections: json.stringList("appSections"),
            adminSections: json.stringList("adminSections"),
            adminActions: json.stringList("adminActions"),
            featureFlags: json.booleanMap("featureFlags")
        )
    }

    func loadAppData(token: String) async throws -> StaffAppData {
        var request = try openRequest(path: "/api/v1/staff/app/data", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let employee = json["employee"] as? [String: Any] ?? [:]
        return StaffAppData(
            employeeId: Self.positiveInt(employee["id"]) ?? 0,
            employeeName: employee["name"] as? String ?? "",
            employeeEmail: employee["email"] as? String ?? "",
            roles: {
                if let arr = employee["roles"] as? [Any] {
                    return arr.compactMap { $0 as? String }
                }
                return json.stringList("roles")
            }(),
            sections: json.stringList("sections"),
            metrics: (json["metrics"] as? [String: Any])?.intMap() ?? [:]
        )
    }

    func loadOnboarding(token: String) async throws -> StaffOnboarding {
        var request = try openRequest(path: "/api/v1/staff/onboarding", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return Self.parseOnboarding(json)
    }

    func initRentalPayment(
        token: String,
        offerAccepted: Bool,
        clubId: Int,
        months: Int = 1
    ) async throws -> RentalPaymentResult {
        var request = try openRequest(path: "/api/v1/staff/rental/init", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "offer_accepted": offerAccepted,
            "club_id": clubId,
            "months": months,
        ])
        let json = try await requireJson(from: request)
        let paymentUrl = (json["payment_url"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let paymentUrl, !paymentUrl.isEmpty else {
            throw StaffApiError.parseFailed("Не получен URL оплаты (payment_url пустой)")
        }
        let onboardingJson = json["onboarding"] as? [String: Any] ?? [:]
        return RentalPaymentResult(
            paymentId: json["payment_id"] as? Int ?? 0,
            status: json["status"] as? String ?? "",
            paymentUrl: paymentUrl,
            onboarding: Self.parseOnboarding(onboardingJson)
        )
    }

    func setActiveRentalClub(token: String, clubId: Int) async throws -> StaffOnboarding {
        var request = try openRequest(path: "/api/v1/staff/rental/active-club", method: "PATCH")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["club_id": clubId])
        let json = try await requireJson(from: request)
        let onboardingJson = json["onboarding"] as? [String: Any] ?? json
        return Self.parseOnboarding(onboardingJson)
    }

    func loadRentalPayments(token: String) async throws -> [RentalPaymentItem] {
        var request = try openRequest(path: "/api/v1/staff/rental/payments", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        return rows.compactMap { row in
            let id = row["id"] as? Int ?? 0
            guard id > 0 else { return nil }
            return RentalPaymentItem(
                id: id,
                status: row["status"] as? String ?? "",
                amountRub: (row["amount_rub"] as? Double)
                    ?? Double((row["amount_kopecks"] as? Int ?? 0)) / 100.0,
                durationMonths: max(1, row["duration_months"] as? Int ?? 1),
                paidAt: (row["paid_at"] as? String)?.nilIfBlank,
                createdAt: (row["created_at"] as? String)?.nilIfBlank,
                clubId: Self.positiveInt(row["club_id"]),
                clubName: (row["club_name"] as? String)?.nilIfBlank
            )
        }
    }

    func rentalPaymentStatus(token: String, paymentId: Int) async throws -> RentalPaymentResult {
        var request = try openRequest(path: "/api/v1/staff/rental/payments/\(paymentId)/status", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let onboardingJson = json["onboarding"] as? [String: Any] ?? [:]
        return RentalPaymentResult(
            paymentId: json["payment_id"] as? Int ?? paymentId,
            status: json["status"] as? String ?? "",
            paymentUrl: (json["payment_url"] as? String)?.nilIfBlank,
            onboarding: Self.parseOnboarding(onboardingJson)
        )
    }

    func createFeedbackTicket(
        token: String,
        subject: String,
        message: String,
        category: String = "other"
    ) async throws -> Int {
        var request = try openRequest(path: "/api/v1/staff/feedback/tickets", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "subject": subject,
            "message": message,
            "category": category,
        ])
        let json = try await requireJson(from: request)
        return json["id"] as? Int ?? 0
    }

    func loadMyFeedbackTickets(token: String) async throws -> [SupportTicketItem] {
        var request = try openRequest(path: "/api/v1/staff/feedback/tickets", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        return rows.map { row in
            SupportTicketItem(
                id: row["id"] as? Int ?? 0,
                subject: row["subject"] as? String ?? "",
                message: row["message"] as? String ?? "",
                category: row["category"] as? String ?? "",
                status: row["status"] as? String ?? "",
                contactEmail: row["contactEmail"] as? String ?? "",
                clientName: row["clientName"] as? String ?? "",
                clientPhone: row["clientPhone"] as? String ?? "",
                clientId: Self.positiveInt(row["clientId"]),
                createdAt: row["createdAt"] as? String ?? ""
            )
        }
    }

    func loadTrainerProfile(token: String) async throws -> TrainerPublicProfile {
        var request = try openRequest(path: "/api/v1/staff/trainer-profile", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return Self.parseTrainerProfile(try await requireJson(from: request))
    }

    func updateTrainerProfile(
        token: String,
        name: String,
        specialization: String,
        description: String,
        phone: String
    ) async throws -> TrainerPublicProfile {
        var request = try openRequest(path: "/api/v1/staff/trainer-profile", method: "PUT")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "name": name,
            "specialization": specialization,
            "description": description,
            "phone": phone,
        ])
        return Self.parseTrainerProfile(try await requireJson(from: request))
    }

    func uploadTrainerPhoto(token: String, imageData: Data, fileName: String = "photo.jpg") async throws -> TrainerPublicProfile {
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = try openRequest(path: "/api/v1/staff/trainer-profile/photo", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"photo\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        return Self.parseTrainerProfile(try await requireJson(from: request))
    }

    func createTraining(
        token: String,
        name: String,
        type: String,
        startAtIso: String,
        endAtIso: String,
        room: String?,
        maxParticipants: Int,
        clientId: Int? = nil
    ) async throws -> ScheduleItem {
        var request = try openRequest(path: "/api/v1/staff/trainings", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        var payload: [String: Any] = [
            "name": name,
            "type": type,
            "start_at": startAtIso,
            "end_at": endAtIso,
            "max_participants": maxParticipants,
        ]
        if let room, !room.isEmpty { payload["room"] = room }
        if let clientId { payload["client_id"] = clientId }
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let json = try await requireJson(from: request)
        guard let training = json["training"] as? [String: Any] else {
            throw StaffApiError.parseFailed("Сервер не вернул созданное занятие")
        }
        return Self.parseScheduleItem(training)
    }

    func bookClientOnTraining(token: String, trainingId: String, clientId: Int) async throws {
        var request = try openRequest(path: "/api/v1/staff/trainings/\(trainingId)/book", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["client_id": clientId])
        _ = try await requireJson(from: request)
    }

    func cancelStaffBooking(token: String, bookingId: String) async throws -> Bool {
        var request = try openRequest(path: "/api/v1/staff/bookings/\(bookingId)", method: "DELETE")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return json["training_removed"] as? Bool ?? false
    }

    func updateTraining(
        token: String,
        trainingId: String,
        name: String,
        startAtIso: String,
        endAtIso: String,
        room: String?
    ) async throws -> ScheduleItem {
        var request = try openRequest(path: "/api/v1/staff/trainings/\(trainingId)", method: "PUT")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "name": name,
            "start_at": startAtIso,
            "end_at": endAtIso,
            "room": room ?? "",
        ])
        let json = try await requireJson(from: request)
        guard let training = json["training"] as? [String: Any] else {
            throw StaffApiError.parseFailed("Сервер не вернул обновлённое занятие")
        }
        return Self.parseScheduleItem(training)
    }

    func loadAdminData(token: String) async throws -> StaffAdminData {
        var request = try openRequest(path: "/api/v1/staff/admin/data", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return StaffAdminData(
            adminSections: json.stringList("adminSections"),
            adminMenu: (json["adminMenu"] as? [String: Any])?.stringMap() ?? [:],
            widgets: json.widgetMap("widgets"),
            canWrite: json["canWrite"] as? Bool ?? false
        )
    }

    func loadSectionData(token: String, mode: String, section: String) async throws -> SectionData {
        let path = "/api/v1/staff/section-data?mode=\(mode)&section=\(section)"
        var request = try openRequest(path: path, method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        return SectionData(
            mode: json["mode"] as? String ?? "",
            section: json["section"] as? String ?? "",
            cards: json.widgetMap("cards")
        )
    }

    func loadSchedule(token: String, from: String? = nil) async throws -> ScheduleData {
        var path = "/api/v1/staff/schedule"
        if let from, !from.isEmpty {
            let encoded = from.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? from
            path += "?from=\(encoded)"
        }
        var request = try openRequest(path: path, method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let dayRows = json["days"] as? [[String: Any]] ?? []
        let days = dayRows.map { row in
            ScheduleDay(
                date: row["date"] as? String ?? "",
                label: row["label"] as? String ?? "",
                count: row["count"] as? Int ?? 0
            )
        }
        let itemRows = json["items"] as? [[String: Any]] ?? []
        let items = itemRows.map { Self.parseScheduleItem($0) }
        return ScheduleData(days: days, items: items)
    }

    func loadList(token: String, section: String) async throws -> [FeedListItem] {
        var request = try openRequest(path: "/api/v1/staff/list?section=\(section)", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        return rows.map { row in
            let id = row["id"] as? Int ?? 0
            let refType = row["refType"] as? String
            return FeedListItem(
                title: row["title"] as? String ?? "",
                subtitle: row["subtitle"] as? String ?? "",
                meta: row["meta"] as? String ?? "",
                id: id > 0 ? id : nil,
                refType: refType?.isEmpty == false ? refType : nil
            )
        }
    }

    func loadSupportTickets(token: String, status: String? = nil) async throws -> SupportTicketsData {
        var path = "/api/v1/staff/support/tickets"
        if let status, !status.isEmpty {
            path += "?status=\(status)"
        }
        var request = try openRequest(path: path, method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        let items = rows.map { row in
            let clientId = row["clientId"] as? Int ?? 0
            return SupportTicketItem(
                id: row["id"] as? Int ?? 0,
                subject: row["subject"] as? String ?? "",
                message: row["message"] as? String ?? "",
                category: row["category"] as? String ?? "",
                status: row["status"] as? String ?? "",
                contactEmail: row["contactEmail"] as? String ?? "",
                clientName: row["clientName"] as? String ?? "",
                clientPhone: row["clientPhone"] as? String ?? "",
                clientId: clientId > 0 ? clientId : nil,
                createdAt: row["createdAt"] as? String ?? ""
            )
        }
        return SupportTicketsData(items: items, newCount: json["newCount"] as? Int ?? 0)
    }

    func loadClients(token: String, query: String = "") async throws -> [ClientSummary] {
        var path = "/api/v1/staff/clients"
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !q.isEmpty {
            let encoded = q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? q
            path += "?q=\(encoded)"
        }
        var request = try openRequest(path: path, method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        return rows.map { row in
            ClientSummary(
                id: row["id"] as? Int ?? 0,
                name: row["name"] as? String ?? "",
                email: row["email"] as? String ?? "",
                phone: row["phone"] as? String ?? "",
                hasActiveBooking: row["hasActiveBooking"] as? Bool ?? false
            )
        }
    }

    func loadClientDetail(token: String, clientId: Int) async throws -> ClientDetail {
        var request = try openRequest(path: "/api/v1/staff/clients/\(clientId)", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let row = json["client"] as? [String: Any] ?? [:]
        let subJson = row["subscription"] as? [String: Any]
        let subscription: ClientSubscription? = subJson.map { sub in
            let endDate = sub["endDate"] as? String
            return ClientSubscription(
                plan: sub["plan"] as? String ?? "",
                status: sub["status"] as? String ?? "",
                endDate: endDate?.isEmpty == false ? endDate : nil,
                visitsUsed: sub["visitsUsed"] as? Int ?? 0,
                visitsTotal: sub["visitsTotal"] as? Int ?? 0
            )
        }
        return ClientDetail(
            id: row["id"] as? Int ?? 0,
            name: row["name"] as? String ?? "",
            email: row["email"] as? String ?? "",
            phone: row["phone"] as? String ?? "",
            bonusPoints: row["bonusPoints"] as? Int ?? 0,
            isBlocked: row["isBlocked"] as? Bool ?? false,
            subscription: subscription,
            recentBookings: (row["recentBookings"] as? [[String: Any]])?.detailRows() ?? [],
            recentTickets: (row["recentTickets"] as? [[String: Any]])?.ticketRows() ?? []
        )
    }

    func registerPushToken(token: String, pushToken: String, platform: String = "ios") async throws -> Bool {
        var request = try openRequest(path: "/api/v1/staff/push-token", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["token": pushToken, "platform": platform])
        let result = try await execute(request)
        return (200...299).contains(result.code)
    }

    func updateSupportTicketStatus(token: String, ticketId: Int, status: String) async throws -> Bool {
        var request = try openRequest(path: "/api/v1/staff/support/tickets/\(ticketId)/status", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["status": status])
        let result = try await execute(request)
        return (200...299).contains(result.code)
    }

    func loadStaffNotifications(token: String) async throws -> StaffNotificationsData {
        var request = try openRequest(path: "/api/v1/staff/notifications", method: "GET")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await requireJson(from: request)
        let rows = json["items"] as? [[String: Any]] ?? []
        let items = rows.map { row in
            StaffNotificationItem(
                id: row["id"] as? Int ?? 0,
                type: row["type"] as? String ?? "",
                title: row["title"] as? String ?? "",
                body: row["body"] as? String ?? "",
                referenceId: row["referenceId"] as? String ?? "",
                createdAt: row["createdAt"] as? String ?? "",
                isRead: row["isRead"] as? Bool ?? false
            )
        }
        return StaffNotificationsData(items: items, unreadCount: json["unreadCount"] as? Int ?? 0)
    }

    func markAllStaffNotificationsRead(token: String) async throws -> Bool {
        var request = try openRequest(path: "/api/v1/staff/notifications/read-all", method: "POST")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let result = try await execute(request)
        return (200...299).contains(result.code)
    }

    func pingServer() async -> Bool {
        do {
            var request = try openRequest(path: "/api/v1/staff/config", method: "GET")
            request.timeoutInterval = 5
            let result = try await execute(request)
            return (200...499).contains(result.code)
        } catch {
            return false
        }
    }

    // MARK: - Private

    private struct HttpResult {
        let code: Int
        let body: String
    }

    private func authRequest(path: String, payload: [String: Any]) async throws -> StaffSession {
        var request = try openRequest(path: path, method: "POST")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let json = try await requireJson(from: request)
        return parseSession(json)
    }

    private func parseSession(_ json: [String: Any]) -> StaffSession {
        let user = json["user"] as? [String: Any] ?? [:]
        return StaffSession(
            accessToken: json["token"] as? String ?? "",
            refreshToken: json["refresh_token"] as? String ?? "",
            userEmail: user["email"] as? String ?? ""
        )
    }

    private static func parseOnboarding(_ json: [String: Any]) -> StaffOnboarding {
        let missing = (json["profile_missing"] as? [Any])?.compactMap { $0 as? String } ?? []
        let catalog = (json["specializations_catalog"] as? [Any])?.compactMap { $0 as? String } ?? []
        var plans: [RentalPlan] = []
        if let plansArr = json["rental_plans"] as? [[String: Any]] {
            for row in plansArr {
                let months = row["months"] as? Int ?? 0
                guard months > 0 else { continue }
                let kopecks = row["amount_kopecks"] as? Int ?? 0
                plans.append(RentalPlan(
                    months: months,
                    label: (row["label"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "\(months) мес.",
                    amountKopecks: kopecks,
                    amountRub: (row["amount_rub"] as? Double) ?? Double(kopecks) / 100.0
                ))
            }
        }
        var clubs: [RentalClubOption] = []
        if let clubsArr = json["rental_clubs"] as? [[String: Any]] {
            for row in clubsArr {
                guard let clubId = positiveInt(row["club_id"]) else { continue }
                let kopecks = row["amount_kopecks"] as? Int ?? 0
                clubs.append(RentalClubOption(
                    clubId: clubId,
                    name: row["name"] as? String ?? "",
                    address: row["address"] as? String ?? "",
                    amountKopecks: kopecks,
                    amountRub: (row["amount_rub"] as? Double) ?? Double(kopecks) / 100.0,
                    paidUntil: (row["paid_until"] as? String)?.nilIfBlank,
                    rentalActive: row["rental_active"] as? Bool ?? false,
                    isActiveClub: row["is_active_club"] as? Bool ?? false,
                    days: max(1, row["days"] as? Int ?? 30),
                    entryQrFormat: {
                        let raw = (row["entry_qr_format"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                        return raw.isEmpty ? "ascii" : raw
                    }()
                ))
            }
        }
        let requiresRental = json["requires_rental"] as? Bool ?? false
        let paidUntil = (json["rental_paid_until"] as? String)?.nilIfBlank
        let activeClubId = positiveInt(json["active_club_id"])
        let staffUserId = positiveInt(json["staff_user_id"])
            ?? positiveInt(json["staffUserId"])
            ?? positiveInt((json["employee"] as? [String: Any])?["id"])
        let topFormat = (json["entry_qr_format"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let resolvedFormat: String = {
            if !topFormat.isEmpty { return topFormat }
            if let active = clubs.first(where: { $0.isActiveClub })?.entryQrFormat { return active }
            if let id = activeClubId, let match = clubs.first(where: { $0.clubId == id })?.entryQrFormat {
                return match
            }
            return "ascii"
        }()
        let rentalActive: Bool
        if json["rental_active"] != nil {
            rentalActive = json["rental_active"] as? Bool ?? false
        } else {
            rentalActive = !requiresRental || StaffRentalAccess.isPaidPeriodActive(paidUntil)
        }
        return StaffOnboarding(
            status: json["status"] as? String ?? "active",
            registrationStatus: json["registration_status"] as? String ?? "approved",
            requiresRental: requiresRental,
            rentalPaidUntil: paidUntil,
            rentalActive: rentalActive,
            offerUrl: json["offer_url"] as? String ?? "https://dobrozal.ru/doc/offer",
            privacyUrl: json["privacy_url"] as? String ?? "https://dobrozal.ru/doc/privacy",
            docsUrl: json["docs_url"] as? String ?? "https://dobrozal.ru/doc",
            rentalAmountKopecks: json["rental_amount_kopecks"] as? Int ?? 0,
            rentalAmountRub: json["rental_amount_rub"] as? Double ?? 0,
            rentalPlans: plans,
            rentalClubs: clubs,
            activeClubId: activeClubId,
            rentalDays: max(1, json["rental_days"] as? Int ?? 30),
            staffUserId: staffUserId,
            entryQrFormat: resolvedFormat,
            profileComplete: json["profile_complete"] as? Bool ?? true,
            profileMissing: missing,
            specializationsCatalog: catalog.isEmpty ? TrainerSpecializationCatalog.default : catalog,
            specializationsMax: max(
                1,
                (json["specializations_max"] as? Int) ?? TrainerSpecializationCatalog.maxSelected
            )
        )
    }

    private static func parseTrainerProfile(_ json: [String: Any]) -> TrainerPublicProfile {
        let catalogRaw = (json["specializations_catalog"] as? [Any])?.compactMap { $0 as? String } ?? []
        let catalog = catalogRaw.isEmpty ? TrainerSpecializationCatalog.default : catalogRaw
        let maxSelected = max(
            1,
            (json["specializations_max"] as? Int) ?? TrainerSpecializationCatalog.maxSelected
        )
        let specsFromArray = (json["specializations"] as? [Any])?.compactMap { $0 as? String } ?? []
        let specs = specsFromArray.isEmpty
            ? TrainerSpecializationCatalog.parseSelected(json["specialization"] as? String ?? "", catalog: catalog)
            : Array(specsFromArray.prefix(maxSelected))
        return TrainerPublicProfile(
            name: json["name"] as? String ?? "",
            specialization: TrainerSpecializationCatalog.join(specs),
            specializations: specs,
            description: Self.cleanString(json["description"] as? String),
            phone: json["phone"] as? String ?? "",
            photoUrl: {
                let raw = Self.cleanString((json["photo_url"] as? String) ?? (json["photoUrl"] as? String))
                guard !raw.isEmpty else { return nil }
                if raw.hasPrefix("http") { return raw }
                return StaffApiUrl.resolve().trimmingCharacters(in: CharacterSet(charactersIn: "/")) + (raw.hasPrefix("/") ? raw : "/\(raw)")
            }(),
            publicationStatus: json["publication_status"] as? String
                ?? json["publicationStatus"] as? String
                ?? "moderation",
            publicationStatusLabel: json["publication_status_label"] as? String
                ?? json["publicationStatusLabel"] as? String
                ?? "На модерации",
            profileComplete: json["profile_complete"] as? Bool
                ?? json["profileComplete"] as? Bool
                ?? true,
            specializationsCatalog: catalog,
            specializationsMax: maxSelected,
            needsModeration: {
                let flag = (json["needs_moderation"] as? Bool)
                    ?? (json["needsModeration"] as? Bool)
                    ?? false
                let status = (json["publication_status"] as? String)
                    ?? (json["publicationStatus"] as? String)
                    ?? ""
                return flag || status == "moderation"
            }()
        )
    }

    private static func cleanString(_ value: String?) -> String {
        guard let value else { return "" }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.lowercased() == "null" { return "" }
        return trimmed
    }

    private static func parseScheduleItem(_ row: [String: Any]) -> ScheduleItem {
        let bookingRows = row["bookings"] as? [[String: Any]] ?? []
        let bookings = bookingRows.compactMap { b -> ScheduleBookingRow? in
            let id: String = {
                if let s = b["id"] as? String, !s.isEmpty { return s }
                if let i = b["id"] as? Int { return String(i) }
                return ""
            }()
            guard !id.isEmpty else { return nil }
            return ScheduleBookingRow(
                id: id,
                clientName: b["client_name"] as? String ?? b["clientName"] as? String ?? "",
                clientId: {
                    if let s = b["client_id"] as? String ?? b["clientId"] as? String, !s.isEmpty {
                        return s
                    }
                    if let i = b["client_id"] as? Int ?? b["clientId"] as? Int, i > 0 {
                        return String(i)
                    }
                    return nil
                }(),
                status: b["status"] as? String ?? ""
            )
        }
        let id: String? = {
            if let s = row["id"] as? String, !s.isEmpty { return s }
            if let i = row["id"] as? Int { return String(i) }
            return nil
        }()
        return ScheduleItem(
            id: id,
            title: row["title"] as? String ?? "",
            trainer: row["trainer"] as? String ?? "",
            type: row["type"] as? String ?? "",
            date: row["date"] as? String ?? "",
            dayLabel: row["dayLabel"] as? String ?? "",
            startTime: row["startTime"] as? String ?? "",
            endTime: row["endTime"] as? String ?? "",
            startAt: row["startAt"] as? String ?? "",
            endAt: row["endAt"] as? String ?? "",
            room: row["room"] as? String ?? "",
            clientNames: row.stringList("clientNames"),
            participants: row["participants"] as? String ?? "",
            maxParticipants: row["maxParticipants"] as? Int,
            currentParticipants: row["currentParticipants"] as? Int,
            bookings: bookings
        )
    }

    private static func positiveInt(_ value: Any?) -> Int? {
        switch value {
        case let i as Int where i > 0: return i
        case let d as Double where Int(d) > 0: return Int(d)
        case let s as String:
            return Int(s.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0 > 0 ? $0 : nil }
        default: return nil
        }
    }

    private func openRequest(path: String, method: String) throws -> URLRequest {
        guard let url = URL(string: baseUrl + path) else {
            throw StaffApiError.parseFailed("Invalid URL")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("utf-8", forHTTPHeaderField: "Accept-Charset")
        return request
    }

    private func execute(_ request: URLRequest) async throws -> HttpResult {
        let (data, response) = try await session.data(for: request)
        let http = response as? HTTPURLResponse
        let code = http?.statusCode ?? 0
        let body = String(data: data, encoding: .utf8) ?? ""
        return HttpResult(code: code, body: body)
    }

    private func requireJson(from request: URLRequest) async throws -> [String: Any] {
        let result = try await execute(request)
        guard (200...299).contains(result.code) else {
            let parsed = parseJson(result.body)
            let detail = parsed?["error"] as? String
                ?? String(result.body.prefix(120))
            let apiCode = (parsed?["code"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            let suffix = apiCode.map { " [\($0)]" } ?? ""
            let message = (detail.isEmpty ? "пустой ответ, код \(result.code)" : detail) + suffix
            throw StaffApiError.http(status: result.code, detail: message)
        }
        guard let json = parseJson(result.body) else {
            throw StaffApiError.parseFailed("Invalid JSON")
        }
        return json
    }

    private func parseJson(_ response: String) -> [String: Any]? {
        let trimmed = response.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
        if trimmed.isEmpty { return nil }
        if trimmed.hasPrefix("<") { return nil }
        guard let data = trimmed.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        return object
    }
}

// MARK: - JSON helpers

private extension String {
    var nilIfBlank: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

private extension Dictionary where Key == String, Value == Any {
    func stringList(_ key: String) -> [String] {
        (self[key] as? [Any])?.compactMap { $0 as? String } ?? []
    }

    func intMap() -> [String: Int] {
        var out: [String: Int] = [:]
        for (k, v) in self {
            if let i = v as? Int { out[k] = i }
            else if let d = v as? Double { out[k] = Int(d) }
        }
        return out
    }

    func stringMap() -> [String: String] {
        var out: [String: String] = [:]
        for (k, v) in self {
            if let s = v as? String { out[k] = s }
        }
        return out
    }

    func booleanMap(_ key: String) -> [String: Bool] {
        guard let dict = self[key] as? [String: Any] else { return [:] }
        var out: [String: Bool] = [:]
        for (k, v) in dict {
            out[k] = v as? Bool ?? false
        }
        return out
    }

    func widgetMap(_ key: String) -> [String: Int] {
        guard let rows = self[key] as? [[String: Any]] else { return [:] }
        var out: [String: Int] = [:]
        for row in rows {
            let k = row["key"] as? String ?? ""
            out[k] = row["value"] as? Int ?? 0
        }
        return out
    }
}

private extension Array where Element == [String: Any] {
    func detailRows() -> [ClientDetailRow] {
        map { row in
            let meta = row["meta"] as? String ?? ""
            let upcoming = row["isUpcoming"] as? Bool ?? Self.metaLooksUpcoming(meta)
            return ClientDetailRow(
                title: row["title"] as? String ?? "",
                meta: meta,
                isUpcoming: upcoming
            )
        }
    }

    private static func metaLooksUpcoming(_ meta: String) -> Bool {
        // meta like "27.08.2026 10:00"
        let parts = meta.split(separator: " ")
        guard let datePart = parts.first else { return false }
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(identifier: "Asia/Vladivostok")
        df.dateFormat = "dd.MM.yyyy"
        guard let day = df.date(from: String(datePart)) else { return false }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Vladivostok") ?? .current
        return day >= cal.startOfDay(for: Date())
    }

    func ticketRows() -> [ClientDetailRow] {
        map { row in
            let status = row["status"] as? String ?? ""
            return ClientDetailRow(
                title: row["subject"] as? String ?? "",
                meta: "\(UiLabels.ticketStatus(status)) · \(row["createdAt"] as? String ?? "")"
            )
        }
    }
}
