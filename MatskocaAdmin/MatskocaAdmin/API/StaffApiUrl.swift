import Foundation

enum StaffApiUrl {
    private static let emulatorUrl = "http://127.0.0.1:8000"
    private static let deviceUrl = "https://worldcashfit.ru"

    static func resolve() -> String {
        #if targetEnvironment(simulator)
        return emulatorUrl
        #else
        return deviceUrl
        #endif
    }

    static func baseUrlLabel() -> String {
        resolve().trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }
}
