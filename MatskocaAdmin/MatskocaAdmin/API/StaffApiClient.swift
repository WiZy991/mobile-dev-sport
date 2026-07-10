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

    func register(email: String, name: String, password: String, role: String) async throws -> StaffSession {
        let payload: [String: Any] = [
            "email": email, "name": name, "password": password, "role": role,
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
            employeeName: employee["name"] as? String ?? "",
            employeeEmail: employee["email"] as? String ?? "",
            sections: json.stringList("sections"),
            metrics: (json["metrics"] as? [String: Any])?.intMap() ?? [:]
        )
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

    func loadSchedule(token: String) async throws -> ScheduleData {
        var request = try openRequest(path: "/api/v1/staff/schedule", method: "GET")
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
        let items = itemRows.map { row in
            ScheduleItem(
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
                participants: row["participants"] as? String ?? ""
            )
        }
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
                phone: row["phone"] as? String ?? ""
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
            let detail = parseJson(result.body)?["error"] as? String
                ?? String(result.body.prefix(120))
            throw StaffApiError.http(status: result.code, detail: detail.isEmpty ? "пустой ответ, код \(result.code)" : detail)
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
            ClientDetailRow(
                title: row["title"] as? String ?? "",
                meta: row["meta"] as? String ?? ""
            )
        }
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
