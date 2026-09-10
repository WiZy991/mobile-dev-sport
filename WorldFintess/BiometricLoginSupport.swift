import Foundation
import LocalAuthentication
import Security

enum BiometricCredentialStoreError: Error {
    case notAvailable
    case keychain(OSStatus)
    case decode
}

/// Хранит refresh-токен в Keychain с биометрией (как `BiometricLoginStore.kt` на Android).
enum BiometricCredentialStore {
    private static let service = "co.worldcashbox.WorldFintess.biometric"
    private static let account = "refresh_token"
    private static let prefsKey = "wf_biometric_login_saved"

    static var hasSavedLogin: Bool {
        UserDefaults.standard.bool(forKey: prefsKey)
    }

    static func save(refreshToken: String) throws {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err) else {
            throw BiometricCredentialStoreError.notAvailable
        }

        let data = Data(refreshToken.utf8)

        var cfErr: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            .biometryCurrentSet,
            &cfErr
        ) else {
            throw BiometricCredentialStoreError.notAvailable
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)

        var attrs = query
        attrs[kSecValueData as String] = data
        attrs[kSecAttrAccessControl as String] = access
        let status = SecItemAdd(attrs as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw BiometricCredentialStoreError.keychain(status)
        }
        UserDefaults.standard.set(true, forKey: prefsKey)
    }

    static func loadRefreshToken(context: LAContext) throws -> String {
        context.localizedReason = "Вход в \(AppConfiguration.appDisplayName)"

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseAuthenticationContext as String: context,
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        guard status == errSecSuccess, let data = out as? Data else {
            throw BiometricCredentialStoreError.keychain(status)
        }
        guard let token = String(data: data, encoding: .utf8), !token.isEmpty else {
            throw BiometricCredentialStoreError.decode
        }
        return token
    }

    /// После нового входа (Сбер / refresh) — обновить сохранённый refresh, если биометрия включена.
    static func reEncryptIfEnabled(newRefreshToken: String) {
        guard hasSavedLogin, !newRefreshToken.isEmpty else { return }
        try? save(refreshToken: newRefreshToken)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        UserDefaults.standard.set(false, forKey: prefsKey)
    }
}

enum BiometryLoginUX {
    static var canUseBiometrics: Bool {
        let ctx = LAContext()
        return ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    static func primaryIconName() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "person.badge.key"
        }
        switch ctx.biometryType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        case .opticID: return "opticid"
        default: return "person.badge.key"
        }
    }

    static func saveToggleLabel() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Вход по биометрии"
        }
        switch ctx.biometryType {
        case .faceID: return "Сохранить для входа по Face ID"
        case .touchID: return "Сохранить для входа по Touch ID"
        case .opticID: return "Сохранить для входа по Optic ID"
        default: return "Сохранить для входа по биометрии"
        }
    }

    static func signInButtonTitle() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Войти по биометрии"
        }
        switch ctx.biometryType {
        case .faceID: return "Войти по Face ID"
        case .touchID: return "Войти по Touch ID"
        case .opticID: return "Войти по Optic ID"
        default: return "Войти по биометрии"
        }
    }

    static func settingsTitle() -> String {
        "Биометрия"
    }

    static func settingsSubtitle() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Быстрый вход в аккаунт"
        }
        switch ctx.biometryType {
        case .faceID: return "Вход по Face ID"
        case .touchID: return "Вход по Touch ID"
        case .opticID: return "Вход по Optic ID"
        default: return "Быстрый вход в аккаунт"
        }
    }

    static func unlockToggleLabel() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Разблокировка по биометрии"
        }
        switch ctx.biometryType {
        case .faceID: return "Разблокировка по Face ID"
        case .touchID: return "Разблокировка по Touch ID"
        case .opticID: return "Разблокировка по Optic ID"
        default: return "Разблокировка по биометрии"
        }
    }

    static func unlockButtonTitle() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Биометрия"
        }
        switch ctx.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "Биометрия"
        }
    }

    static func biometryShortName() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "биометрией"
        }
        switch ctx.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "биометрией"
        }
    }

    static func hardwareHint() -> String {
        switch LAContext().biometryType {
        case .faceID: return "Настройте Face ID в системных настройках iPhone."
        case .touchID: return "Добавьте отпечаток в системных настройках."
        default: return "Настройте биометрию в системных настройках."
        }
    }
}
