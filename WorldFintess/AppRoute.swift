import Foundation

/// Экраны вне нижних вкладок (как `NavGraph` в Android).
enum AppRoute: Hashable {
    case trainingDetail(String)
    case subscriptionPlans
    case paymentPending(Int)
    case qrCode
    case editProfile
    case referral
    case notifications
    case settings
    case changePassword
    case networkInfo
    case help
    case about
    case shop
    case clubs
    case clubDetail(String)
    case clubInfo
    case selectPreferredClub
    case lockers
    case guestPass
    case documents
    case purchaseHistory
    case trainers
    case trainerDetail(String)
    case personalTraining
    case trainingDiary
    case legalDocument(LegalDocumentKind)
    case legalPdf(LegalPdfAsset)
}
