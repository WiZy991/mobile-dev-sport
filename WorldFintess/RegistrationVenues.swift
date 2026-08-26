import Foundation

/// Карточка зала на экране регистрации (данные с `GET /clubs`).
struct RegistrationVenueCard: Identifiable, Hashable {
    let clubId: String
    let title: String
    let addressLines: String
    let imageUrl: String?
    /// Локальный ассет, если нет URL из CRM.
    let imageAssetName: String?

    var id: String { clubId }

    init(from club: ClubItem) {
        clubId = club.id
        title = club.name
        addressLines = club.address
        imageUrl = club.imageUrl
        imageAssetName = RegistrationVenues.fallbackAssetName(forClubId: club.id)
    }
}

enum RegistrationVenues {
    /// Опциональные локальные фото по известным id (если CRM ещё без image_url).
    static func fallbackAssetName(forClubId clubId: String) -> String? {
        switch clubId {
        case "1": return "registration_club_mall"
        case "2": return "registration_club_kupera"
        case "11": return "registration_club_mall"
        default: return nil
        }
    }

    static func clubItem(for card: RegistrationVenueCard) -> ClubItem {
        ClubItem(
            id: card.clubId,
            name: card.title,
            address: card.addressLines,
            phone: nil,
            email: nil,
            workingHours: nil,
            latitude: 0,
            longitude: 0,
            amenities: [],
            maxCapacity: nil,
            imageUrl: card.imageUrl
        )
    }
}
