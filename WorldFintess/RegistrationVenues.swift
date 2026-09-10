import Foundation

/// Карточка зала на экране регистрации (`RegistrationVenues.kt`).
struct RegistrationVenueCard: Identifiable, Hashable {
    let clubId: String
    let title: String
    let addressLines: String
    let imageUrl: String?
    /// Локальный ассет, если нет URL из CRM.
    let imageAssetName: String?
    let openForRegistration: Bool

    var id: String { clubId }

    init(
        clubId: String,
        title: String,
        addressLines: String,
        imageUrl: String? = nil,
        imageAssetName: String?,
        openForRegistration: Bool = true
    ) {
        self.clubId = clubId
        self.title = title
        self.addressLines = addressLines
        self.imageUrl = imageUrl
        self.imageAssetName = imageAssetName
        self.openForRegistration = openForRegistration
    }

    init(from club: ClubItem) {
        clubId = club.id
        title = club.name
        addressLines = club.address
        imageUrl = club.imageUrl
        imageAssetName = RegistrationVenues.imageAssetName(forName: club.name, address: club.address)
        openForRegistration = true
    }
}

enum RegistrationVenues {
    /// Резерв, если `GET /clubs` пуст/ошибка (как Android `RegistrationVenues.orderedCards`).
    private static let allCards: [RegistrationVenueCard] = [
        RegistrationVenueCard(
            clubId: "12",
            title: "ТЦ Формат",
            addressLines: "ул. Центральная, 18, 2 этаж",
            imageAssetName: "registration_club_mall",
            openForRegistration: true
        ),
        RegistrationVenueCard(
            clubId: "2",
            title: "ТЦ Новый де Фриз",
            addressLines: "ул. Купера, 2, 2 этаж",
            imageAssetName: "registration_club_kupera",
            openForRegistration: true
        ),
        RegistrationVenueCard(
            clubId: "11",
            title: "ТЦ Седанка Сити",
            addressLines: "г. Владивосток, ул. Полетаева 6Д",
            imageAssetName: "registration_club_mall",
            openForRegistration: true
        ),
    ]

    static var orderedCards: [RegistrationVenueCard] {
        allCards.filter(\.openForRegistration)
    }

    /// Фото по названию/адресу (id в CRM у сетей разные).
    static func imageAssetName(forName name: String, address: String) -> String {
        let hay = "\(name) \(address)".lowercased()
        let kupera = ["купера", "де фриз", "де-фриз", "дефриз"]
        if kupera.contains(where: { hay.contains($0) }) {
            return "registration_club_kupera"
        }
        return "registration_club_mall"
    }

    static func fallbackAssetName(forClubId clubId: String) -> String? {
        allCards.first(where: { $0.clubId == clubId })?.imageAssetName
            ?? imageAssetName(forName: "", address: "")
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
