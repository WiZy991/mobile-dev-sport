import Foundation
import SwiftUI

/// Глобальное состояние: сессия, пользователь, доступ к API (MVVM — ViewModel’и получают API отсюда).
@MainActor
final class WorldFitnessAppState: ObservableObject {
    let api = FitnessAPI()

    @Published private(set) var isLoggedIn = false
    @Published private(set) var currentUser: User?

    private let userDefaultsKey = "wf_cached_user_json"

    init() {
        restoreSession()
    }

    func restoreSession() {
        guard let access = KeychainStore.get(.accessToken),
              let refresh = KeychainStore.get(.refreshToken),
              let data = UserDefaults.standard.data(forKey: userDefaultsKey),
              let user = try? AppJSON.decoder().decode(User.self, from: data)
        else {
            isLoggedIn = false
            currentUser = nil
            api.setCredentials(access: nil, refresh: nil, userId: nil)
            return
        }
        api.setCredentials(access: access, refresh: refresh, userId: user.id)
        currentUser = user
        isLoggedIn = true
    }

    func applyAuth(_ response: AuthResponse) {
        KeychainStore.set(response.token, for: .accessToken)
        KeychainStore.set(response.refreshToken, for: .refreshToken)
        if let data = try? AppJSON.encoder().encode(response.user) {
            UserDefaults.standard.set(data, forKey: userDefaultsKey)
        }
        api.setCredentials(access: response.token, refresh: response.refreshToken, userId: response.user.id)
        currentUser = response.user
        isLoggedIn = true
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
        await api.logout()
        KeychainStore.clearTokens()
        BiometricCredentialStore.clear()
        UserDefaults.standard.removeObject(forKey: userDefaultsKey)
        api.setCredentials(access: nil, refresh: nil, userId: nil)
        currentUser = nil
        isLoggedIn = false
    }
}
