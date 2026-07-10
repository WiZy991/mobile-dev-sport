import Foundation
import Observation

@Observable
@MainActor
final class LoginController {
    var email = ""
    var name = ""
    var password = ""
    var selectedRole: RoleOptionUi?
    var roles: [RoleOptionUi] = []
    var configSummary = ""
    var statusMessage: String?
    var errorMessage: String?
    var isLoading = false
    var isCheckingSession = false

    private let env: AppEnvironment
    private var autoLoginStarted = false

    private let roleOptions: [RoleOptionUi] = [
        RoleOptionUi(label: "Тренер", role: "ROLE_TRAINER"),
        RoleOptionUi(label: "Менеджер", role: "ROLE_MANAGER"),
        RoleOptionUi(label: "Финансы", role: "ROLE_FINANCE"),
        RoleOptionUi(label: "Наблюдатель", role: "ROLE_VIEWER"),
        RoleOptionUi(label: "Поддержка", role: "ROLE_SUPPORT"),
        RoleOptionUi(label: "Администратор", role: "ROLE_ADMIN"),
        RoleOptionUi(label: "Суперадминистратор", role: "ROLE_SUPER_ADMIN"),
    ]

    var onAuthenticated: (() -> Void)?

    init(env: AppEnvironment) {
        self.env = env
        roles = roleOptions
        selectedRole = roleOptions.first
    }

    func tryAutoLogin() {
        guard !autoLoginStarted, env.session != nil else { return }
        autoLoginStarted = true
        runAsync(status: "Проверяем доступ...", checkingSession: true) {
            let config = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadConfig(token: token)
            }
            self.env.completeAuth(self.env.session!, config: config)
            self.env.registerPushIfLoggedIn()
            self.onAuthenticated?()
        }
    }

    func login() {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty else {
            errorMessage = "Введите email"
            return
        }
        guard !password.isEmpty else {
            errorMessage = "Введите пароль"
            return
        }
        runAsync(status: "Вход...") {
            let session = try await self.env.apiClient.login(
                email: trimmedEmail,
                password: self.password
            )
            self.env.session = session
            self.env.sessionStore.saveSession(session)
            self.env.sessionStore.clearConfig()
            let config = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadConfig(token: token)
            }
            self.configSummary = "Роли: \(config.roles.map { UiLabels.roleTitle($0) }.joined(separator: ", "))\nДоступы загружены"
            self.env.completeAuth(session, config: config)
            self.env.registerPushIfLoggedIn()
            self.onAuthenticated?()
        }
    }

    func register() {
        runAsync(status: "Регистрация...") {
            let session = try await self.env.apiClient.register(
                email: self.email.trimmingCharacters(in: .whitespacesAndNewlines),
                name: self.name.trimmingCharacters(in: .whitespacesAndNewlines),
                password: self.password,
                role: self.selectedRole?.role ?? self.roleOptions[0].role
            )
            self.env.session = session
            self.env.sessionStore.saveSession(session)
            self.env.sessionStore.clearConfig()
            let config = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadConfig(token: token)
            }
            self.env.completeAuth(session, config: config)
            self.env.registerPushIfLoggedIn()
            self.onAuthenticated?()
        }
    }

    private func runAsync(status: String, checkingSession: Bool = false, action: @escaping () async throws -> Void) {
        isLoading = true
        isCheckingSession = checkingSession
        statusMessage = status
        errorMessage = nil
        Task { @MainActor in
            do {
                try await action()
                isLoading = false
                isCheckingSession = false
                statusMessage = nil
            } catch {
                isLoading = false
                isCheckingSession = false
                statusMessage = nil
                errorMessage = UserFacingError.message(error)
            }
        }
    }
}
