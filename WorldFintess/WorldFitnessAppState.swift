import Foundation
import SwiftUI

/// Глобальное состояние: сессия, пользователь, доступ к API (MVVM — ViewModel’и получают API отсюда).
@MainActor
final class WorldFitnessAppState: ObservableObject {
    let api = FitnessAPI()

    @Published private(set) var isLoggedIn = false
    @Published private(set) var currentUser: User?
    /// Как Android `sessionReady`: не показываем UI, пока bootstrap не завершён.
    @Published private(set) var sessionReady = false
    /// Активная сессия оплаты — чтобы не дублировать экран при deep link.
    @Published var currentPaymentId: Int?
    /// Запрос навигации на экран ожидания оплаты (из магазина и др.).
    @Published var paymentNavigationRequest: Int?
    /// Перезагрузка абонементов в профиле после оплаты.
    @Published var subscriptionsRevision = UUID()
    /// Показать мастер настройки PIN/биометрии после регистрации.
    @Published var pendingSecuritySetup = false

    private let userDefaultsKey = "wf_cached_user_json"
    /// Уже хотя бы раз успешно входили в приложение на этом устройстве → показываем сценарий «вход», а не «первая регистрация».
    static let completedSignupOnceKey = "wf_completed_signup_once"

    init() {
        api.onSessionRefreshed = { [weak self] auth in
            Task { @MainActor in
                self?.persistRefreshedTokens(auth)
            }
        }
        api.onSessionInvalidated = { [weak self] in
            Task { @MainActor in
                self?.clearLocalSessionAfterInvalidRefresh()
            }
        }
        restoreSession()
        Task { await self.runBootstrap() }
    }

    /// Android `bootstrapSession`: refresh + профиль, затем открываем UI.
    /// Минимум ~0.9 с на splash, чтобы экран загрузки не мелькал.
    private func runBootstrap() async {
        let started = Date()
        let snap = api.snapshotCredentials()
        if let refresh = snap.refresh?.trimmingCharacters(in: .whitespacesAndNewlines), !refresh.isEmpty {
            await api.bootstrapRefresh()
            if isLoggedIn, let profile = try? await api.getProfile() {
                updateCachedUser(profile)
            }
        }
        let elapsed = Date().timeIntervalSince(started)
        let remaining = max(0, 0.9 - elapsed)
        if remaining > 0 {
            try? await Task.sleep(nanoseconds: UInt64(remaining * 1_000_000_000))
        }
        sessionReady = true
    }

    func restoreSession() {
        guard let access = KeychainStore.get(.accessToken),
              let refresh = KeychainStore.get(.refreshToken),
              let data = UserDefaults.standard.data(forKey: userDefaultsKey),
              let user = try? AppJSON.decoder().decode(User.self, from: data)
        else {
            // Как Android: достаточно refresh — access подтянется bootstrap/refresh.
            if let refresh = KeychainStore.get(.refreshToken), !refresh.isEmpty,
               let data = UserDefaults.standard.data(forKey: userDefaultsKey),
               let user = try? AppJSON.decoder().decode(User.self, from: data)
            {
                api.setCredentials(access: KeychainStore.get(.accessToken), refresh: refresh, userId: user.id)
                currentUser = user
                isLoggedIn = true
                return
            }
            isLoggedIn = false
            currentUser = nil
            api.setCredentials(access: nil, refresh: nil, userId: nil)
            return
        }
        api.setCredentials(access: access, refresh: refresh, userId: user.id)
        currentUser = user
        isLoggedIn = true
    }

    func applyAuth(_ response: AuthResponse, markReturningCustomerOnDevice: Bool = true) {
        KeychainStore.set(response.token, for: .accessToken)
        KeychainStore.set(response.refreshToken, for: .refreshToken)
        if markReturningCustomerOnDevice {
            UserDefaults.standard.set(true, forKey: Self.completedSignupOnceKey)
        }
        if let data = try? AppJSON.encoder().encode(response.user) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
        api.setCredentials(access: response.token, refresh: response.refreshToken, userId: response.user.id)
        currentUser = response.user
        isLoggedIn = true
        AppResumeLock.clearInactivityMarker()
        BiometricCredentialStore.reEncryptIfEnabled(newRefreshToken: response.refreshToken)
    }

    /// После тихого `auth/refresh` — те же токены в Keychain (раньше обновлялась только память → «сессия истекла»).
    private func persistRefreshedTokens(_ response: AuthResponse) {
        KeychainStore.set(response.token, for: .accessToken)
        KeychainStore.set(response.refreshToken, for: .refreshToken)
        if let data = try? AppJSON.encoder().encode(response.user) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
        currentUser = response.user
        isLoggedIn = true
        BiometricCredentialStore.reEncryptIfEnabled(newRefreshToken: response.refreshToken)
    }

    /// Refresh 401/403 — локальный выход без повторного `auth/logout` (сервер уже не принимает токен).
    private func clearLocalSessionAfterInvalidRefresh() {
        KeychainStore.clearTokens()
        UserDefaults.standard.removeObject(forKey: userDefaultsKey)
        api.setCredentials(access: nil, refresh: nil, userId: nil)
        currentUser = nil
        isLoggedIn = false
        currentPaymentId = nil
        paymentNavigationRequest = nil
        AppResumeLock.clearInactivityMarker()
    }

    /// После регистрации с выбором зала — считаем пользователя «возвращающимся» на этом устройстве.
    func markReturningCustomerOnDeviceAfterSuccessfulSignupFlow() {
        UserDefaults.standard.set(true, forKey: Self.completedSignupOnceKey)
    }

    func updateCachedUser(_ user: User) {
        if let data = try? AppJSON.encoder().encode(user) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
        currentUser = user
        let snap = api.snapshotCredentials()
        api.setCredentials(access: snap.access, refresh: snap.refresh, userId: user.id)
    }

    func logout() async {
        // Как Android: при сохранённой биометрии не отзываем refresh на сервере.
        if !BiometricCredentialStore.hasSavedLogin {
            await api.logout()
        }
        KeychainStore.clearTokens()
        // Биометрию для входа не сбрасываем — как на Android; отключение только из настроек.
        UserDefaults.standard.removeObject(forKey: userDefaultsKey)
        api.setCredentials(access: nil, refresh: nil, userId: nil)
        currentUser = nil
        isLoggedIn = false
        currentPaymentId = nil
        paymentNavigationRequest = nil
        AppResumeLock.clearInactivityMarker()
    }

    /// Удаление аккаунта на сервере и очистка локальной сессии.
    func deleteAccount() async throws {
        try await api.deleteAccount()
        BiometricCredentialStore.clear()
        AppPinStore.clearPin()
        await logout()
    }
}
