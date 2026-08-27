import Foundation

/// Доступ к QR прохода: оплата аренды по календарю клуба (Владивосток) — как Android `StaffRentalAccess`.
enum StaffRentalAccess {
    private static let clubTimeZone = TimeZone(identifier: "Asia/Vladivostok") ?? .current

    static func isPaidPeriodActive(_ rentalPaidUntilIso: String?) -> Bool {
        guard let iso = rentalPaidUntilIso?.trimmingCharacters(in: .whitespacesAndNewlines), !iso.isEmpty,
              let untilDate = parsePaidUntilDate(iso) else {
            return false
        }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = clubTimeZone
        let today = cal.startOfDay(for: Date())
        return untilDate >= today
    }

    static func canShowEntryQr(
        staffUserId: Int,
        status: String,
        requiresRental: Bool,
        rentalPaidUntilIso: String?,
        rentalActiveFromServer: Bool? = nil,
        activeClubRentalOk: Bool? = nil
    ) -> Bool {
        if staffUserId <= 0 { return false }
        if status == "pending_approval" || status == "rejected" { return false }
        if !requiresRental { return true }
        if let ok = activeClubRentalOk { return ok }
        return isPaidPeriodActive(rentalPaidUntilIso) || rentalActiveFromServer == true
    }

    static func entryQrBlockedMessage(
        staffUserId: Int,
        status: String,
        requiresRental: Bool,
        rentalPaidUntilIso: String?,
        hasPaidClubsButWrongActive: Bool = false
    ) -> String? {
        if staffUserId <= 0 { return "Не удалось определить учётную запись." }
        if status == "pending_approval" || status == "rejected" {
            return "QR откроется после одобрения регистрации."
        }
        if !requiresRental { return nil }
        if hasPaidClubsButWrongActive {
            return "Выберите оплаченный зал в профиле, чтобы открыть QR прохода."
        }
        if !isPaidPeriodActive(rentalPaidUntilIso) {
            if rentalPaidUntilIso?.isEmpty != false {
                return "Оплатите аренду зала, чтобы пройти по QR."
            }
            return "Срок аренды выбранного зала истёк. Продлите оплату или смените адрес."
        }
        return nil
    }

    private static func parsePaidUntilDate(_ iso: String) -> Date? {
        let normalized = iso.replacingOccurrences(of: " ", with: "T")
        let prefix19 = String(normalized.prefix(19))
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = clubTimeZone
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        if let dt = formatter.date(from: prefix19) {
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = clubTimeZone
            return cal.startOfDay(for: dt)
        }
        formatter.dateFormat = "yyyy-MM-dd"
        if let dt = formatter.date(from: String(normalized.prefix(10))) {
            return dt
        }
        return nil
    }
}
