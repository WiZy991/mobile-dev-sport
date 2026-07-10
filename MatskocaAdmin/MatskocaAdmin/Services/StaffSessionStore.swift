import Foundation

final class StaffSessionStore {
    private let defaults = UserDefaults.standard
    private let suiteKey = "staff_session"

    func saveSession(_ session: StaffSession) {
        defaults.set(session.accessToken, forKey: "\(suiteKey).access")
        defaults.set(session.refreshToken, forKey: "\(suiteKey).refresh")
        defaults.set(session.userEmail, forKey: "\(suiteKey).email")
    }

    func loadSession() -> StaffSession? {
        guard let access = defaults.string(forKey: "\(suiteKey).access"),
              let refresh = defaults.string(forKey: "\(suiteKey).refresh") else {
            return nil
        }
        let email = defaults.string(forKey: "\(suiteKey).email") ?? ""
        return StaffSession(accessToken: access, refreshToken: refresh, userEmail: email)
    }

    func saveConfig(_ config: RoleConfig) {
        defaults.set(config.roles.joined(separator: ","), forKey: "\(suiteKey).roles")
        defaults.set(config.appSections.joined(separator: ","), forKey: "\(suiteKey).app_sections")
        defaults.set(config.adminSections.joined(separator: ","), forKey: "\(suiteKey).admin_sections")
        defaults.set(config.adminActions.joined(separator: ","), forKey: "\(suiteKey).admin_actions")
    }

    func loadConfig() -> RoleConfig? {
        guard let rolesRaw = defaults.string(forKey: "\(suiteKey).roles"), !rolesRaw.isEmpty else {
            return nil
        }
        return RoleConfig(
            roles: parseCsv(rolesRaw),
            appSections: parseCsv(defaults.string(forKey: "\(suiteKey).app_sections")),
            adminSections: parseCsv(defaults.string(forKey: "\(suiteKey).admin_sections")),
            adminActions: parseCsv(defaults.string(forKey: "\(suiteKey).admin_actions")),
            featureFlags: [:]
        )
    }

    func clearConfig() {
        defaults.removeObject(forKey: "\(suiteKey).roles")
        defaults.removeObject(forKey: "\(suiteKey).app_sections")
        defaults.removeObject(forKey: "\(suiteKey).admin_sections")
        defaults.removeObject(forKey: "\(suiteKey).admin_actions")
    }

    func clearAll() {
        let keys = [
            "\(suiteKey).access", "\(suiteKey).refresh", "\(suiteKey).email",
            "\(suiteKey).roles", "\(suiteKey).app_sections", "\(suiteKey).admin_sections",
            "\(suiteKey).admin_actions", "\(suiteKey).unread_notifications", "\(suiteKey).push_token",
        ]
        keys.forEach { defaults.removeObject(forKey: $0) }
    }

    func getLastUnreadNotificationCount() -> Int {
        defaults.object(forKey: "\(suiteKey).unread_notifications") as? Int ?? -1
    }

    func setLastUnreadNotificationCount(_ count: Int) {
        defaults.set(count, forKey: "\(suiteKey).unread_notifications")
    }

    func getOrCreatePushToken() -> String {
        if let existing = defaults.string(forKey: "\(suiteKey).push_token"), !existing.isEmpty {
            return existing
        }
        let token = "staff-local-\(UUID().uuidString)"
        defaults.set(token, forKey: "\(suiteKey).push_token")
        return token
    }

    private func parseCsv(_ raw: String?) -> [String] {
        guard let raw, !raw.isEmpty else { return [] }
        return raw.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }
}
