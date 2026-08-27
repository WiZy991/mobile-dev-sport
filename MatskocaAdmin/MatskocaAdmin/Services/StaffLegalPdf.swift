import Foundation

enum StaffLegalPdf: String, Identifiable, CaseIterable {
    case userAgreement
    case privacy
    case proOffer
    case dobrozalPrivacy

    var id: String { rawValue }

    var fileName: String {
        switch self {
        case .userAgreement: return "user_agreement"
        case .privacy: return "privacy"
        case .proOffer: return "pro_offer"
        case .dobrozalPrivacy: return "dobrozal_privacy"
        }
    }

    var title: String {
        switch self {
        case .userAgreement: return "Пользовательское соглашение"
        case .privacy: return "Политика конфиденциальности"
        case .proOffer: return "Оферта для специалистов"
        case .dobrozalPrivacy: return "Политика обработки и защиты персональных данных Клуба"
        }
    }

    /// Bundle URL from `Resources/Legal/*.pdf`.
    var bundleURL: URL? {
        Bundle.main.url(forResource: fileName, withExtension: "pdf", subdirectory: "Legal")
            ?? Bundle.main.url(forResource: fileName, withExtension: "pdf")
    }
}
