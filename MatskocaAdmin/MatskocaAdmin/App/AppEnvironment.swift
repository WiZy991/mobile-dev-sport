import Foundation
import Observation

enum AppRoute: Hashable {
    case adminHub
    case adminSection(String)
    case clientDetail(Int)
    case entryQr
    case rental
    case feedback
    case trainerProfile
    case legalPdf(String)
}

enum StaffAuthGate: Equatable {
    case checking
    case work
    case onboarding
    case trainerProfile
}

@Observable
@MainActor
final class AppEnvironment {
    let apiClient: StaffApiClient
    let sessionStore: StaffSessionStore

    var session: StaffSession?
    var roleConfig: RoleConfig?
    var isAuthenticated: Bool = false
    var authGate: StaffAuthGate = .checking
    /// Ошибка проверки onboarding (не открываем Work «вслепую»).
    var authGateError: String?
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
        self.authGate = self.isAuthenticated ? .checking : .checking
    }

    func resolveAuthGate() async {
        guard isAuthenticated else {
            authGate = .checking
            authGateError = nil
            return
        }
        authGate = .checking
        authGateError = nil
        do {
            let onboarding = try await withRefresh { token in
                try await apiClient.loadOnboarding(token: token)
            }
            if onboarding.status == "active" {
                authGate = .work
            } else if onboarding.status == "needs_profile" {
                authGate = .trainerProfile
            } else {
                authGate = .onboarding
            }
        } catch {
            // Как Android MainActivity: при сбое маршрутизации не пускаем в Work.
            authGateError = UserFacingError.message(error)
            authGate = .checking
        }
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
        authGate = .checking
    }

    func logout() {
        sessionStore.clearAll()
        session = nil
        roleConfig = nil
        isAuthenticated = false
        authGate = .checking
        authGateError = nil
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
