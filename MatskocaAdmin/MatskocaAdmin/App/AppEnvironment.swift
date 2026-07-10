import Foundation
import Observation

enum AppRoute: Hashable {
    case adminHub
    case adminSection(String)
    case clientDetail(Int)
}

@Observable
@MainActor
final class AppEnvironment {
    let apiClient: StaffApiClient
    let sessionStore: StaffSessionStore

    var session: StaffSession?
    var roleConfig: RoleConfig?
    var isAuthenticated: Bool = false
    var navigationPath: [AppRoute] = []
    var pendingWorkTab: WorkTab?

    init(
        apiClient: StaffApiClient = StaffApiClient(),
        sessionStore: StaffSessionStore = StaffSessionStore()
    ) {
        self.apiClient = apiClient
        self.sessionStore = sessionStore
        self.session = sessionStore.loadSession()
        self.roleConfig = sessionStore.loadConfig()
        self.isAuthenticated = session != nil && roleConfig != nil
    }

    func withRefresh<T>(_ action: (String) async throws -> T) async throws -> T {
        guard var current = session else {
            throw StaffApiError.http(status: 401, detail: "Сессия не найдена")
        }
        do {
            return try await action(current.accessToken)
        } catch let error as StaffApiError {
            if case .http(let status, _) = error, status == 401 {
                let refreshed = try await apiClient.refresh(refreshToken: current.refreshToken)
                session = refreshed
                sessionStore.saveSession(refreshed)
                return try await action(refreshed.accessToken)
            }
            throw error
        }
    }

    func completeAuth(_ newSession: StaffSession, config: RoleConfig) {
        session = newSession
        roleConfig = config
        sessionStore.saveSession(newSession)
        sessionStore.saveConfig(config)
        isAuthenticated = true
    }

    func logout() {
        sessionStore.clearAll()
        session = nil
        roleConfig = nil
        isAuthenticated = false
        navigationPath = []
        pendingWorkTab = nil
    }

    func registerPushIfLoggedIn() {
        guard let session else { return }
        let token = sessionStore.getOrCreatePushToken()
        Task {
            _ = try? await apiClient.registerPushToken(token: session.accessToken, pushToken: token, platform: "ios")
        }
    }

    func navigateToWorkTab(_ tab: WorkTab) {
        pendingWorkTab = tab
        navigationPath.removeAll()
    }
}
