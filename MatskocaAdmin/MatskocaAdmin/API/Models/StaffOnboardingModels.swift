import Foundation

struct StaffOnboarding {
    let status: String
    let registrationStatus: String
    let requiresRental: Bool
    let rentalPaidUntil: String?
    let rentalActive: Bool
    let offerUrl: String
    let privacyUrl: String
    let docsUrl: String
    let rentalAmountKopecks: Int
    let rentalAmountRub: Double
    let rentalPlans: [RentalPlan]
    let rentalClubs: [RentalClubOption]
    let activeClubId: Int?
    let rentalDays: Int
    let staffUserId: Int?
    let entryQrFormat: String
    let profileComplete: Bool
    let profileMissing: [String]
    let specializationsCatalog: [String]
    let specializationsMax: Int

    var activeClub: RentalClubOption? {
        if let active = rentalClubs.first(where: { $0.isActiveClub }) {
            return active
        }
        if let id = activeClubId {
            return rentalClubs.first(where: { $0.clubId == id })
        }
        return nil
    }

    var activeClubPaidUntil: String? {
        activeClub?.paidUntil ?? rentalPaidUntil
    }

    var resolvedEntryQrFormat: String {
        if let fmt = activeClub?.entryQrFormat, !fmt.isEmpty { return fmt }
        if !entryQrFormat.isEmpty { return entryQrFormat }
        return "ascii"
    }

    var activeClubRentalOk: Bool {
        if !requiresRental { return true }
        if let club = activeClub {
            return club.rentalActive || StaffRentalAccess.isPaidPeriodActive(club.paidUntil)
        }
        return rentalActive || StaffRentalAccess.isPaidPeriodActive(rentalPaidUntil)
    }
}

struct RentalClubOption: Identifiable, Equatable {
    var id: Int { clubId }
    let clubId: Int
    let name: String
    let address: String
    let amountKopecks: Int
    let amountRub: Double
    let paidUntil: String?
    let rentalActive: Bool
    let isActiveClub: Bool
    let days: Int
    let entryQrFormat: String

    /// Подпись без дубля «название · адрес», как Android `compactHallLabel`.
    var title: String {
        StaffHallLabel.compact(name: name, address: address)
    }

    /// Короткое имя для чипов QR / профиля.
    var shortName: String {
        let n = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return n.isEmpty ? title : n
    }
}

enum StaffHallLabel {
    static func compact(name: String?, address: String?) -> String {
        let n = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let a = address?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if n.isEmpty { return a }
        if a.isEmpty { return n }
        if a.localizedCaseInsensitiveContains(n) { return n }
        if n.localizedCaseInsensitiveContains(a) { return n }
        let aNoCity = a.replacingOccurrences(
            of: #"^г\.\s*[^,]+,\s*"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        ).trimmingCharacters(in: .whitespacesAndNewlines)
        if !aNoCity.isEmpty {
            if aNoCity.caseInsensitiveCompare(n) == .orderedSame { return n }
            if aNoCity.localizedCaseInsensitiveContains(n) { return n }
            if n.localizedCaseInsensitiveContains(aNoCity) { return n }
        }
        return "\(n) · \(a)"
    }
}

struct RentalPlan {
    let months: Int
    let label: String
    let amountKopecks: Int
    let amountRub: Double
}

struct RentalPaymentItem: Identifiable {
    let id: Int
    let status: String
    let amountRub: Double
    let durationMonths: Int
    let paidAt: String?
    let createdAt: String?
    let clubId: Int?
    let clubName: String?
}

struct RentalPaymentResult {
    let paymentId: Int
    let status: String
    let paymentUrl: String?
    let onboarding: StaffOnboarding
}

struct TrainerPublicProfile {
    let name: String
    let specialization: String
    let specializations: [String]
    let description: String
    let phone: String
    let photoUrl: String?
    let publicationStatus: String
    let publicationStatusLabel: String
    let profileComplete: Bool
    let specializationsCatalog: [String]
    let specializationsMax: Int
    let needsModeration: Bool
}
