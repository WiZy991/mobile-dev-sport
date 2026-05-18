import Foundation
import LocalAuthentication
import Security

// MARK: - Keychain: email/пароль только с Face ID / Touch ID

enum BiometricCredentialStoreError: Error {
    case notAvailable
    case keychain(OSStatus)
    case decode
}

enum BiometricCredentialStore {
    private static let service = "co.worldcashbox.WorldFintess.biometric"
    private static let account = "login_credentials"
    private static let prefsKey = "wf_biometric_login_saved"

    struct Credentials: Codable {
        let email: String
        let password: String
    }

    static var hasSavedLogin: Bool {
        UserDefaults.standard.bool(forKey: prefsKey)
    }

    static func save(email: String, password: String) throws {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &err) else {
            throw BiometricCredentialStoreError.notAvailable
        }

        let creds = Credentials(email: email, password: password)
        let data = try JSONEncoder().encode(creds)

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

    static func loadCredentials(context: LAContext) throws -> Credentials {
        context.localizedReason = "Вход в аккаунт фитнес-клуба"

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
        guard let creds = try? JSONDecoder().decode(Credentials.self, from: data) else {
            throw BiometricCredentialStoreError.decode
        }
        return creds
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

    /// Вызвать после `canEvaluatePolicy` на том же типе контекста для актуального типа.
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
        case .faceID: return "Сохранить для входа по Face ID"
        case .touchID: return "Сохранить для входа по Touch ID"
        case .opticID: return "Сохранить для входа по Optic ID"
        default: return "Сохранить для входа по биометрии"
        }
    }

    static func signInButtonTitle() -> String {
        let ctx = LAContext()
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return "Войти по биометрии"
        }
        switch ctx.biometryType {
        case .faceID: return "Войти с Face ID"
        case .touchID: return "Войти с Touch ID"
        case .opticID: return "Войти с Optic ID"
        default: return "Войти по биометрии"
        }
    }
}
