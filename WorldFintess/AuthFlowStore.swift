import Foundation

/// Локальные флаги auth-потока (`AuthFlowStore.kt`) — без Sber.
enum AuthFlowStore {
    private static let defaults = UserDefaults.standard
    private static let otpTicketKey = "wf_otp_registration_ticket"
    private static let otpPhoneKey = "wf_otp_registration_phone"

    static var hasCompletedRegistration: Bool {
        defaults.bool(forKey: WorldFitnessAppState.completedSignupOnceKey)
    }

    static func saveOtpRegistration(ticket: String, phone: String) {
        defaults.set(ticket, forKey: otpTicketKey)
        defaults.set(phone, forKey: otpPhoneKey)
    }

    static func peekOtpTicket() -> String? {
        defaults.string(forKey: otpTicketKey)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    static func peekOtpPhone() -> String? {
        defaults.string(forKey: otpPhoneKey)?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
    }

    static func consumeOtpRegistration() -> (ticket: String, phone: String)? {
        guard let ticket = peekOtpTicket(), let phone = peekOtpPhone() else { return nil }
        clearOtpRegistration()
        return (ticket, phone)
    }

    static func clearOtpRegistration() {
        defaults.removeObject(forKey: otpTicketKey)
        defaults.removeObject(forKey: otpPhoneKey)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
