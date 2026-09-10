import CryptoKit
import Foundation
import Security

/// Код-пароль приложения (отдельно от пароля аккаунта и биометрического входа).
enum AppPinStore {
    private static let enabledKey = "wf_app_pin_enabled"
    private static let biometricUnlockKey = "wf_app_biometric_unlock"
    private static let service = "co.worldcashbox.WorldFintess.apppin"
    private static let hashAccount = "pin_hash"
    private static let saltAccount = "pin_salt"

    static var isPinEnabled: Bool {
        UserDefaults.standard.bool(forKey: enabledKey)
    }

    static var isBiometricUnlockEnabled: Bool {
        UserDefaults.standard.bool(forKey: biometricUnlockKey)
    }

    static func setBiometricUnlockEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: biometricUnlockKey)
    }

    static func setPin(_ pin: String) throws {
        guard pin.count >= 4, pin.count <= 6, pin.allSatisfy(\.isNumber) else {
            throw AppPinError.invalidFormat
        }
        let salt = randomSalt()
        let hash = hashPin(pin, salt: salt)
        try saveKeychain(account: saltAccount, data: salt)
        try saveKeychain(account: hashAccount, data: hash)
        UserDefaults.standard.set(true, forKey: enabledKey)
    }

    static func verifyPin(_ pin: String) -> Bool {
        guard isPinEnabled,
              let salt = loadKeychain(account: saltAccount),
              let stored = loadKeychain(account: hashAccount)
        else { return false }
        return hashPin(pin, salt: salt) == stored
    }

    static func clearPin() {
        deleteKeychain(account: hashAccount)
        deleteKeychain(account: saltAccount)
        UserDefaults.standard.set(false, forKey: enabledKey)
        UserDefaults.standard.set(false, forKey: biometricUnlockKey)
    }

    private static func randomSalt() -> Data {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }

    private static func hashPin(_ pin: String, salt: Data) -> Data {
        var input = Data(pin.utf8)
        input.append(salt)
        return Data(SHA256.hash(data: input))
    }

    private static func saveKeychain(account: String, data: Data) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var attrs = query
        attrs[kSecValueData as String] = data
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        let status = SecItemAdd(attrs as CFDictionary, nil)
        guard status == errSecSuccess else { throw AppPinError.keychain(status) }
    }

    private static func loadKeychain(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    private static func deleteKeychain(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

enum AppPinError: Error, LocalizedError {
    case invalidFormat
    case mismatch
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidFormat: return "Код должен содержать 4–6 цифр"
        case .mismatch: return "Коды не совпадают"
        case .keychain: return "Не удалось сохранить код-пароль"
        }
    }
}
