import Foundation

// MARK: - Auth / User

struct User: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let email: String
    let phone: String
    let name: String
    let avatarUrl: String?
    let bonusPoints: Int
    let passportVerificationStatus: String?
    let dateOfBirth: String?
    let createdAt: String?
    let isVerified: Bool
    let sberId: String?
    /// Идентификатор клуба в CRM (`PUT user/profile`, `club_id` в ответах API).
    let clubId: String?
    /// Название клуба (`club_name`), показывается в шапке профиля как на Android.
    let clubName: String?
    /// Формат QR входа клуба: `ascii` | `wiegand` (`entry_qr_format` из CRM).
    let entryQrFormat: String?

    init(
        id: String,
        email: String,
        phone: String,
        name: String,
        avatarUrl: String? = nil,
        bonusPoints: Int = 0,
        passportVerificationStatus: String? = nil,
        dateOfBirth: String? = nil,
        createdAt: String? = nil,
        isVerified: Bool = false,
        sberId: String? = nil,
        clubId: String? = nil,
        clubName: String? = nil,
        entryQrFormat: String? = nil
    ) {
        self.id = id
        self.email = email
        self.phone = phone
        self.name = name
        self.avatarUrl = avatarUrl
        self.bonusPoints = bonusPoints
        self.passportVerificationStatus = passportVerificationStatus
        self.dateOfBirth = dateOfBirth
        self.createdAt = createdAt
        self.isVerified = isVerified
        self.sberId = sberId
        self.clubId = clubId
        self.clubName = clubName
        self.entryQrFormat = entryQrFormat
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // null/отсутствующие поля не должны ронять логин и профиль.
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        email = (try? c.decode(String.self, forKey: .email)) ?? ""
        phone = (try? c.decode(String.self, forKey: .phone)) ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        bonusPoints = try c.decodeIfPresent(Int.self, forKey: .bonusPoints) ?? 0
        passportVerificationStatus = try c.decodeIfPresent(String.self, forKey: .passportVerificationStatus)
        dateOfBirth = try c.decodeIfPresent(String.self, forKey: .dateOfBirth)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        isVerified = try c.decodeIfPresent(Bool.self, forKey: .isVerified) ?? false
        sberId = try c.decodeIfPresent(String.self, forKey: .sberId)
        if let cs = try? c.decode(Int.self, forKey: .clubId) {
            clubId = String(cs)
        } else {
            clubId = try c.decodeIfPresent(String.self, forKey: .clubId)
        }
        clubName = try c.decodeIfPresent(String.self, forKey: .clubName)
        entryQrFormat = try c.decodeIfPresent(String.self, forKey: .entryQrFormat)
    }

    enum CodingKeys: String, CodingKey {
        case id, email, phone, name, avatarUrl, bonusPoints
        case passportVerificationStatus, dateOfBirth, createdAt, isVerified, sberId, clubId, clubName, entryQrFormat
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(email, forKey: .email)
        try c.encode(phone, forKey: .phone)
        try c.encode(name, forKey: .name)
        try c.encodeIfPresent(avatarUrl, forKey: .avatarUrl)
        try c.encode(bonusPoints, forKey: .bonusPoints)
        try c.encodeIfPresent(passportVerificationStatus, forKey: .passportVerificationStatus)
        try c.encodeIfPresent(dateOfBirth, forKey: .dateOfBirth)
        try c.encodeIfPresent(createdAt, forKey: .createdAt)
        try c.encode(isVerified, forKey: .isVerified)
        try c.encodeIfPresent(sberId, forKey: .sberId)
        try c.encodeIfPresent(clubId, forKey: .clubId)
        try c.encodeIfPresent(clubName, forKey: .clubName)
        try c.encodeIfPresent(entryQrFormat, forKey: .entryQrFormat)
    }
}

struct AuthResponse: Codable, Sendable {
    let token: String
    let refreshToken: String
    let user: User

    enum CodingKeys: String, CodingKey {
        case token, refreshToken, user
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        token = (try? c.decode(String.self, forKey: .token)) ?? ""
        refreshToken = (try? c.decode(String.self, forKey: .refreshToken)) ?? ""
        user = try c.decode(User.self, forKey: .user)
    }
}

struct LoginRequest: Codable, Sendable {
    let email: String
    let password: String
}

struct LoginHintRequest: Codable, Sendable {
    let email: String
}

struct LoginHintResponse: Codable, Sendable {
    let message: String
    let code: String

    enum CodingKeys: String, CodingKey {
        case message, code
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        message = (try? c.decode(String.self, forKey: .message)) ?? ""
        code = (try? c.decode(String.self, forKey: .code)) ?? ""
    }
}

struct ChangePasswordRequest: Codable, Sendable {
    let currentPassword: String
    let newPassword: String
}

struct NotificationSettings: Codable, Hashable, Sendable {
    var pushEnabled: Bool
    var emailEnabled: Bool
    var trainingReminders: Bool
    var scheduleChanges: Bool
    var promoNotifications: Bool

    static let `default` = NotificationSettings(
        pushEnabled: true,
        emailEnabled: true,
        trainingReminders: true,
        scheduleChanges: true,
        promoNotifications: false
    )

    enum CodingKeys: String, CodingKey {
        case pushEnabled, emailEnabled, trainingReminders, scheduleChanges, promoNotifications
    }

    init(
        pushEnabled: Bool,
        emailEnabled: Bool,
        trainingReminders: Bool,
        scheduleChanges: Bool,
        promoNotifications: Bool
    ) {
        self.pushEnabled = pushEnabled
        self.emailEnabled = emailEnabled
        self.trainingReminders = trainingReminders
        self.scheduleChanges = scheduleChanges
        self.promoNotifications = promoNotifications
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = Self.default
        pushEnabled = try c.decodeIfPresent(Bool.self, forKey: .pushEnabled) ?? d.pushEnabled
        emailEnabled = try c.decodeIfPresent(Bool.self, forKey: .emailEnabled) ?? d.emailEnabled
        trainingReminders = try c.decodeIfPresent(Bool.self, forKey: .trainingReminders) ?? d.trainingReminders
        scheduleChanges = try c.decodeIfPresent(Bool.self, forKey: .scheduleChanges) ?? d.scheduleChanges
        promoNotifications = try c.decodeIfPresent(Bool.self, forKey: .promoNotifications) ?? d.promoNotifications
    }
}

struct ClubShopConfig: Codable, Hashable, Sendable {
    var tabOrder: [String]?
    var defaultTab: String?
    var hideEmptyTabs: Bool?
    var counts: ClubShopCounts?

    var resolvedTabOrder: [String] {
        let order = tabOrder ?? ["subscriptions", "services", "goods"]
        return order.isEmpty ? ["subscriptions", "services", "goods"] : order
    }

    var resolvedDefaultTab: String {
        let t = (defaultTab ?? "subscriptions").trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? "subscriptions" : t
    }

    var shouldHideEmptyTabs: Bool { hideEmptyTabs ?? true }
}

struct ClubShopCounts: Codable, Hashable, Sendable {
    var services: Int?
    var goods: Int?
    var subscriptions: Int?
}

struct ClubSocialLink: Codable, Hashable, Sendable {
    var type: String?
    var label: String?
    var url: String
    var color: String?

    init(type: String? = nil, label: String? = nil, url: String, color: String? = nil) {
        self.type = type
        self.label = label
        self.url = url
        self.color = color
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decodeIfPresent(String.self, forKey: .type)
        label = try c.decodeIfPresent(String.self, forKey: .label)
        url = (try? c.decode(String.self, forKey: .url)) ?? ""
        color = try c.decodeIfPresent(String.self, forKey: .color)
    }

    enum CodingKeys: String, CodingKey { case type, label, url, color }

    var displayTitle: String {
        let fromLabel = label?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !fromLabel.isEmpty { return fromLabel }
        switch (type ?? "").lowercased() {
        case "vk": return "ВКонтакте"
        case "telegram": return "Telegram"
        case "whatsapp": return "WhatsApp"
        case "website": return "Сайт"
        case "youtube": return "YouTube"
        case "instagram": return "Instagram"
        default: return "Ссылка"
        }
    }
}

struct ClubNetworkInfo: Codable, Hashable, Sendable {
    var about: String?
    var socialVk: String?
    var socialTelegram: String?
    var website: String?
    var socialLinks: [ClubSocialLink]?

    /// Ссылки для UI: сначала `social_links` из CRM, иначе legacy vk/telegram/website.
    var resolvedSocialLinks: [ClubSocialLink] {
        let fromList = (socialLinks ?? []).filter { !$0.url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if !fromList.isEmpty { return fromList }
        var legacy: [ClubSocialLink] = []
        if let website, !website.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            legacy.append(ClubSocialLink(type: "website", label: "Сайт", url: website))
        }
        if let socialVk, !socialVk.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            legacy.append(ClubSocialLink(type: "vk", label: "ВКонтакте", url: socialVk))
        }
        if let socialTelegram, !socialTelegram.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            legacy.append(ClubSocialLink(type: "telegram", label: "Telegram", url: socialTelegram))
        }
        return legacy
    }
}

struct RegisterRequest: Codable, Sendable {
    let email: String
    let password: String
    let phone: String
    let name: String
    let registrationType: String?
    let dateOfBirth: String?
    let gender: String?
    let passportSeries: String?
    let passportNumber: String?
    let passportIssuedBy: String?
    let passportIssueDate: String?
    let registrationAddress: String?
    let promoCode: String?
    let newsletter: Bool?
    let clubId: String?
    let referralSource: String?
    let referralSourceOther: String?

    enum CodingKeys: String, CodingKey {
        case email, password, phone, name, gender, newsletter
        case registrationType, dateOfBirth, passportSeries, passportNumber
        case passportIssuedBy, passportIssueDate, registrationAddress, promoCode, clubId
        case referralSource, referralSourceOther
    }
}

// MARK: - Legal documents (`LegalDocument.kt`, GET `legal/{slug}`)

struct LegalDocumentResponse: Codable, Sendable {
    let title: String
    let body: String?
    let fields: [LegalDocumentField]?

    enum CodingKeys: String, CodingKey {
        case title, body, fields
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = (try? c.decode(String.self, forKey: .title)) ?? ""
        body = try c.decodeIfPresent(String.self, forKey: .body)
        fields = try c.decodeIfPresent([LegalDocumentField].self, forKey: .fields)
    }
}

struct LegalDocumentField: Codable, Hashable, Sendable, Identifiable {
    let label: String
    let value: String

    var id: String { label }

    enum CodingKeys: String, CodingKey {
        case label, value
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        label = (try? c.decode(String.self, forKey: .label)) ?? ""
        value = (try? c.decode(String.self, forKey: .value)) ?? ""
    }
}

enum LegalDocumentKind: String, Hashable, CaseIterable, Identifiable {
    case requisites
    case terms
    case privacy
    case clientAgreement
    case trainerAgreement
    case personalDataConsent

    var id: String { rawValue }

    var title: String {
        switch self {
        case .requisites: return "Реквизиты"
        case .terms: return "Договор-оферта"
        case .privacy: return "Политика конфиденциальности"
        case .clientAgreement: return "Договор с клиентом"
        case .trainerAgreement: return "Договор с тренером"
        case .personalDataConsent: return "Согласие на обработку персональных данных"
        }
    }

    var apiSlug: String {
        switch self {
        case .requisites: return "requisites"
        case .terms: return "license_agreement"
        case .privacy: return "privacy"
        case .clientAgreement: return "client-agreement"
        case .trainerAgreement: return "trainer-agreement"
        case .personalDataConsent: return "personal-data-consent"
        }
    }
}

struct UserStats: Codable, Sendable {
    let totalVisits: Int
    let streakDays: Int
    let achievements: [Achievement]

    enum CodingKeys: String, CodingKey {
        case totalVisits, streakDays, achievements
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        totalVisits = try c.decodeIfPresent(Int.self, forKey: .totalVisits) ?? 0
        streakDays = try c.decodeIfPresent(Int.self, forKey: .streakDays) ?? 0
        achievements = (try? c.decode([Achievement].self, forKey: .achievements)) ?? []
    }
}

struct Achievement: Codable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String
    let unlocked: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, description, unlocked
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        description = (try? c.decode(String.self, forKey: .description)) ?? ""
        unlocked = try c.decodeIfPresent(Bool.self, forKey: .unlocked) ?? false
    }
}

struct PurchaseItem: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let productName: String
    let quantity: Int
    let price: Double
    let total: Double
    let paymentMethod: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id, productName, quantity, price, total, paymentMethod, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        productName = (try? c.decode(String.self, forKey: .productName)) ?? ""
        quantity = try c.decodeIfPresent(Int.self, forKey: .quantity) ?? 0
        price = (try? c.decode(Double.self, forKey: .price))
            ?? (try? c.decode(Int.self, forKey: .price)).map(Double.init)
            ?? 0
        total = (try? c.decode(Double.self, forKey: .total))
            ?? (try? c.decode(Int.self, forKey: .total)).map(Double.init)
            ?? 0
        paymentMethod = (try? c.decode(String.self, forKey: .paymentMethod)) ?? ""
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
    }
}

// MARK: - Trainings

enum TrainingKind: String, Codable, Sendable {
    case group
    case personal
    case extra
}

enum TrainingIntensity: String, Codable, Sendable {
    case low
    case medium
    case high
}

struct Trainer: Codable, Hashable, Sendable {
    let id: String?
    let name: String
    let photoUrl: String?
    let specialization: String?
    let rating: Double?

    enum CodingKeys: String, CodingKey {
        case id, name, photoUrl, specialization, rating
    }

    init(id: String?, name: String, photoUrl: String?, specialization: String?, rating: Double?) {
        self.id = id
        self.name = name
        self.photoUrl = photoUrl
        self.specialization = specialization
        self.rating = rating
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) {
            id = s
        } else if let n = try? c.decode(Int.self, forKey: .id) {
            id = String(n)
        } else {
            id = nil
        }
        // Тренировка без назначенного тренера: бэкенд может прислать name=null.
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        photoUrl = try c.decodeIfPresent(String.self, forKey: .photoUrl)
        specialization = try c.decodeIfPresent(String.self, forKey: .specialization)
        if let d = try? c.decode(Double.self, forKey: .rating) {
            rating = d
        } else if let i = try? c.decode(Int.self, forKey: .rating) {
            rating = Double(i)
        } else {
            rating = nil
        }
    }
}

struct Training: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String?
    let type: TrainingKind
    let trainer: Trainer
    let startTime: String
    let endTime: String
    let durationMinutes: Int
    let maxParticipants: Int
    let currentParticipants: Int
    let room: String
    let isBooked: Bool
    let intensity: TrainingIntensity?
    let imageUrl: String?

    var spotsLeft: Int { max(0, maxParticipants - currentParticipants) }
    var isFull: Bool { spotsLeft <= 0 }

    enum CodingKeys: String, CodingKey {
        case id, name, description, type, trainer, startTime, endTime, durationMinutes
        case maxParticipants, currentParticipants, room, isBooked, intensity, imageUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        description = try c.decodeIfPresent(String.self, forKey: .description)
        // Неизвестный/пустой тип из импорта CRM не должен ронять весь список.
        type = (try? c.decode(TrainingKind.self, forKey: .type)) ?? .group
        trainer = (try? c.decode(Trainer.self, forKey: .trainer)) ?? Trainer(id: nil, name: "", photoUrl: nil, specialization: nil, rating: nil)
        startTime = (try? c.decode(String.self, forKey: .startTime)) ?? ""
        endTime = (try? c.decode(String.self, forKey: .endTime)) ?? ""
        if let v = try? c.decode(Int.self, forKey: .durationMinutes) {
            durationMinutes = v
        } else if let d = try? c.decode(Double.self, forKey: .durationMinutes) {
            durationMinutes = Int(d)
        } else {
            durationMinutes = 0
        }
        maxParticipants = (try? c.decode(Int.self, forKey: .maxParticipants)) ?? 0
        currentParticipants = (try? c.decode(Int.self, forKey: .currentParticipants)) ?? 0
        room = try c.decodeIfPresent(String.self, forKey: .room) ?? ""
        isBooked = try c.decodeIfPresent(Bool.self, forKey: .isBooked) ?? false
        // Неизвестная intensity (не low/medium/high) не должна ронять расписание/записи.
        if let raw = try? c.decode(String.self, forKey: .intensity) {
            intensity = TrainingIntensity(rawValue: raw)
        } else {
            intensity = nil
        }
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encodeIfPresent(description, forKey: .description)
        try c.encode(type, forKey: .type)
        try c.encode(trainer, forKey: .trainer)
        try c.encode(startTime, forKey: .startTime)
        try c.encode(endTime, forKey: .endTime)
        try c.encode(durationMinutes, forKey: .durationMinutes)
        try c.encode(maxParticipants, forKey: .maxParticipants)
        try c.encode(currentParticipants, forKey: .currentParticipants)
        try c.encode(room, forKey: .room)
        try c.encode(isBooked, forKey: .isBooked)
        try c.encodeIfPresent(intensity, forKey: .intensity)
        try c.encodeIfPresent(imageUrl, forKey: .imageUrl)
    }
}

/// Статус брони: Symfony отдаёт `waiting`, в Android enum также есть `waiting_list`.
struct Booking: Codable, Hashable, Sendable {
    let id: String
    let training: Training
    let status: String
    let bookedAt: String

    var isUpcomingList: Bool {
        status == "confirmed" || status == "waiting" || status == "waiting_list"
    }

    var isPastList: Bool {
        status == "completed" || status == "cancelled"
    }

    enum CodingKeys: String, CodingKey {
        case id, training, status, bookedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        training = try c.decode(Training.self, forKey: .training)
        status = (try? c.decode(String.self, forKey: .status)) ?? ""
        bookedAt = (try? c.decode(String.self, forKey: .bookedAt)) ?? ""
    }
}

// MARK: - Subscriptions

enum SubscriptionKind: String, Codable, Sendable {
    case unlimited
    case limited
    case personal
    case group
}

enum SubscriptionState: String, Codable, Sendable {
    case active
    case frozen
    case expired
    case pending
    case cancelled
}

struct Subscription: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let type: SubscriptionKind
    let startDate: String
    let endDate: String?
    let status: SubscriptionState
    let visitsTotal: Int?
    let visitsUsed: Int
    let freezeDaysTotal: Int
    let freezeDaysUsed: Int
    let isFrozen: Bool
    let price: Double
    let description: String?
    let clubName: String?

    var visitsLeft: Int? {
        guard let t = visitsTotal else { return nil }
        return max(0, t - visitsUsed)
    }

    var freezeDaysLeft: Int { freezeDaysTotal - freezeDaysUsed }

    var formattedEndDate: String {
        guard let endDate, !endDate.isEmpty else { return "Без срока" }
        return String(endDate.prefix(10))
    }

    enum CodingKeys: String, CodingKey {
        case id, name, type, startDate, endDate, status, visitsTotal, visitsUsed
        case freezeDaysTotal, freezeDaysUsed, isFrozen, price, description, clubName
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        // Неизвестные значения enum из импортов CRM не должны ронять список абонементов.
        type = (try? c.decode(SubscriptionKind.self, forKey: .type)) ?? .limited
        startDate = (try? c.decode(String.self, forKey: .startDate)) ?? ""
        endDate = try c.decodeIfPresent(String.self, forKey: .endDate)
        status = (try? c.decode(SubscriptionState.self, forKey: .status)) ?? .active
        visitsTotal = try c.decodeIfPresent(Int.self, forKey: .visitsTotal)
        visitsUsed = (try? c.decode(Int.self, forKey: .visitsUsed)) ?? 0
        freezeDaysTotal = (try? c.decode(Int.self, forKey: .freezeDaysTotal)) ?? 0
        freezeDaysUsed = (try? c.decode(Int.self, forKey: .freezeDaysUsed)) ?? 0
        isFrozen = (try? c.decode(Bool.self, forKey: .isFrozen)) ?? (status == .frozen)
        price = (try? c.decode(Double.self, forKey: .price))
            ?? (try? c.decode(Int.self, forKey: .price)).map(Double.init)
            ?? 0
        description = try c.decodeIfPresent(String.self, forKey: .description)
        clubName = try c.decodeIfPresent(String.self, forKey: .clubName)
    }
}

struct SubscriptionPlan: Codable, Hashable, Sendable {
    let id: String?
    let name: String?
    let description: String?
    let price: Double
    /// Каталожная цена до скидки группы (если `price` уже со скидкой).
    let originalPrice: Double?
    let groupDiscountPercent: Double?
    let durationDays: Int?
    let visitsCount: Int?
    let type: SubscriptionKind?
    let features: [String]?
    let isPopular: Bool

    var safeId: String { id ?? "plan-0" }
    var safeName: String { name ?? "" }
    var safeDescription: String { description ?? "" }
    var safeDurationDays: Int { durationDays ?? 0 }
    var safeFeatures: [String] { features ?? [] }
    /// Цена для зачёркивания: каталог или текущая до промокода.
    var catalogPrice: Double { originalPrice ?? price }

    enum CodingKeys: String, CodingKey {
        case id, name, description, price, originalPrice, groupDiscountPercent
        case durationDays, visitsCount, type, features, isPopular
    }

    init(
        id: String?,
        name: String?,
        description: String?,
        price: Double,
        durationDays: Int?,
        visitsCount: Int?,
        type: SubscriptionKind?,
        features: [String]?,
        isPopular: Bool,
        originalPrice: Double? = nil,
        groupDiscountPercent: Double? = nil
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.price = price
        self.originalPrice = originalPrice
        self.groupDiscountPercent = groupDiscountPercent
        self.durationDays = durationDays
        self.visitsCount = visitsCount
        self.type = type
        self.features = features
        self.isPopular = isPopular
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let s = try? c.decode(String.self, forKey: .id) {
            id = s
        } else if let n = try? c.decode(Int.self, forKey: .id) {
            id = String(n)
        } else {
            id = nil
        }
        name = try c.decodeIfPresent(String.self, forKey: .name)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        price = (try? c.decode(Double.self, forKey: .price))
            ?? (try? c.decode(Int.self, forKey: .price)).map(Double.init)
            ?? 0
        originalPrice = (try? c.decode(Double.self, forKey: .originalPrice))
            ?? (try? c.decode(Int.self, forKey: .originalPrice)).map(Double.init)
        groupDiscountPercent = (try? c.decode(Double.self, forKey: .groupDiscountPercent))
            ?? (try? c.decode(Int.self, forKey: .groupDiscountPercent)).map(Double.init)
        durationDays = try c.decodeIfPresent(Int.self, forKey: .durationDays)
        visitsCount = try c.decodeIfPresent(Int.self, forKey: .visitsCount)
        // Неизвестный type в CRM не должен ронять весь каталог тарифов.
        if let raw = try? c.decode(String.self, forKey: .type) {
            type = SubscriptionKind(rawValue: raw)
        } else {
            type = nil
        }
        features = (try? c.decode([String].self, forKey: .features)) ?? []
        isPopular = try c.decodeIfPresent(Bool.self, forKey: .isPopular) ?? false
    }
}

extension Booking: Identifiable {}

struct PurchaseSubscriptionRequest: Codable, Sendable {
    let planId: String
    let promoCode: String?
}

struct SubscriptionPurchaseErrorBody: Codable, Sendable {
    let code: String?
    let message: String?
    let error: String?
    let authorizeUrl: String?
}

struct PromoCodeRequest: Codable, Sendable {
    let promoCode: String
}

struct PromoValidationResponse: Codable, Sendable {
    let promoValid: Bool
    let promoCode: String?
    let promoError: String?
    let discountPercent: Double?
    let discountAmount: Double?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        promoValid = try c.decodeIfPresent(Bool.self, forKey: .promoValid) ?? false
        promoCode = try c.decodeIfPresent(String.self, forKey: .promoCode)
        promoError = try c.decodeIfPresent(String.self, forKey: .promoError)
        discountPercent = try c.decodeIfPresent(Double.self, forKey: .discountPercent)
        discountAmount = try c.decodeIfPresent(Double.self, forKey: .discountAmount)
    }
}

struct SubscriptionPaymentQuoteResponse: Codable, Sendable {
    let planId: String?
    let originalPrice: Double
    let finalPrice: Double
    let discountAmount: Double
    let promoValid: Bool
    let promoError: String?

    enum CodingKeys: String, CodingKey {
        case planId, originalPrice, finalPrice, discountAmount, promoValid, promoError
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        planId = try c.decodeIfPresent(String.self, forKey: .planId)
        originalPrice = (try? c.decode(Double.self, forKey: .originalPrice))
            ?? (try? c.decode(Int.self, forKey: .originalPrice)).map(Double.init)
            ?? 0
        finalPrice = (try? c.decode(Double.self, forKey: .finalPrice))
            ?? (try? c.decode(Int.self, forKey: .finalPrice)).map(Double.init)
            ?? 0
        discountAmount = (try? c.decode(Double.self, forKey: .discountAmount))
            ?? (try? c.decode(Int.self, forKey: .discountAmount)).map(Double.init)
            ?? 0
        promoValid = try c.decodeIfPresent(Bool.self, forKey: .promoValid) ?? false
        promoError = try c.decodeIfPresent(String.self, forKey: .promoError)
    }
}

struct SubscriptionPaymentInitResponse: Codable, Sendable {
    let paymentId: Int
    let status: String
    let paymentUrl: String?
    let amount: Double
    let finalPrice: Double
    let discountAmount: Double
    let originalPrice: Double
    let expiresAt: String?
    let failureReason: String?
    let subscription: PaymentSubscriptionSummary?

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        paymentId = try c.decodeIfPresent(Int.self, forKey: .paymentId) ?? 0
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "pending"
        paymentUrl = try c.decodeIfPresent(String.self, forKey: .paymentUrl)
        amount = try c.decodeIfPresent(Double.self, forKey: .amount) ?? 0
        finalPrice = try c.decodeIfPresent(Double.self, forKey: .finalPrice) ?? 0
        discountAmount = try c.decodeIfPresent(Double.self, forKey: .discountAmount) ?? 0
        originalPrice = try c.decodeIfPresent(Double.self, forKey: .originalPrice) ?? 0
        expiresAt = try c.decodeIfPresent(String.self, forKey: .expiresAt)
        failureReason = try c.decodeIfPresent(String.self, forKey: .failureReason)
        subscription = try c.decodeIfPresent(PaymentSubscriptionSummary.self, forKey: .subscription)
    }
}

struct PaymentSubscriptionSummary: Codable, Sendable {
    let id: String
    let name: String
    let status: String

    enum CodingKeys: String, CodingKey {
        case id, name, status
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        status = (try? c.decode(String.self, forKey: .status)) ?? ""
    }
}

// MARK: - Products / Shop

struct Product: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String?
    let price: Double
    let category: String

    enum CodingKeys: String, CodingKey {
        case id, name, description, price, category
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        description = try c.decodeIfPresent(String.self, forKey: .description)
        price = (try? c.decode(Double.self, forKey: .price))
            ?? (try? c.decode(Int.self, forKey: .price)).map(Double.init)
            ?? 0
        category = (try? c.decode(String.self, forKey: .category)) ?? "service"
    }
}

struct PurchaseProductRequest: Codable, Sendable {
    let quantity: Int
    let paymentMethod: String
}

struct PurchaseProductResponse: Codable, Sendable {
    let success: Bool
    let saleId: Int
    let product: String
    let quantity: Int
    let total: Double

    enum CodingKeys: String, CodingKey {
        case success, saleId, product, quantity, total
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = try c.decodeIfPresent(Bool.self, forKey: .success) ?? true
        saleId = try c.decodeIfPresent(Int.self, forKey: .saleId) ?? 0
        product = (try? c.decode(String.self, forKey: .product)) ?? ""
        quantity = try c.decodeIfPresent(Int.self, forKey: .quantity) ?? 0
        total = (try? c.decode(Double.self, forKey: .total))
            ?? (try? c.decode(Int.self, forKey: .total)).map(Double.init)
            ?? 0
    }
}

// MARK: - Club / occupancy

struct ClubItem: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let address: String
    let phone: String?
    let email: String?
    let workingHours: String?
    let latitude: Double
    let longitude: Double
    let amenities: [String]
    let maxCapacity: Int?
    let imageUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, name, address, phone, email, workingHours, latitude, longitude, amenities, maxCapacity, imageUrl
    }

    init(
        id: String,
        name: String,
        address: String,
        phone: String?,
        email: String?,
        workingHours: String?,
        latitude: Double,
        longitude: Double,
        amenities: [String],
        maxCapacity: Int?,
        imageUrl: String? = nil
    ) {
        self.id = id
        self.name = name
        self.address = address
        self.phone = phone
        self.email = email
        self.workingHours = workingHours
        self.latitude = latitude
        self.longitude = longitude
        self.amenities = amenities
        self.maxCapacity = maxCapacity
        self.imageUrl = imageUrl
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        address = (try? c.decode(String.self, forKey: .address)) ?? ""
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        email = try c.decodeIfPresent(String.self, forKey: .email)
        workingHours = try c.decodeIfPresent(String.self, forKey: .workingHours)
        // Клубы без координат в CRM не должны ронять список клубов.
        latitude = (try? c.decode(Double.self, forKey: .latitude)) ?? 0
        longitude = (try? c.decode(Double.self, forKey: .longitude)) ?? 0
        amenities = (try? c.decode([String].self, forKey: .amenities)) ?? []
        maxCapacity = try c.decodeIfPresent(Int.self, forKey: .maxCapacity)
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl)
    }
}

/// Принудительное/мягкое обновление приложения из CRM (`app_update` в club/info).
struct AppUpdateInfo: Codable, Hashable, Sendable {
    let androidMinVersionCode: Int
    let iosMinVersionCode: Int
    let force: Bool
    let iosForce: Bool?
    let message: String?
    let iosMessage: String?

    var resolvedForceForIOS: Bool { iosForce ?? force }
    var resolvedMessageForIOS: String? {
        let ios = iosMessage?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !ios.isEmpty { return ios }
        return message
    }

    enum CodingKeys: String, CodingKey {
        case androidMinVersionCode, iosMinVersionCode, force, iosForce, message, iosMessage
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        androidMinVersionCode = (try? c.decode(Int.self, forKey: .androidMinVersionCode)) ?? 0
        iosMinVersionCode = (try? c.decode(Int.self, forKey: .iosMinVersionCode)) ?? 0
        force = (try? c.decode(Bool.self, forKey: .force)) ?? false
        iosForce = try c.decodeIfPresent(Bool.self, forKey: .iosForce)
        message = try c.decodeIfPresent(String.self, forKey: .message)
        iosMessage = try c.decodeIfPresent(String.self, forKey: .iosMessage)
    }
}

struct ClubInfo: Codable, Hashable, Sendable {
    let id: String?
    let promoTitle: String?
    let promoSubtitle: String?
    /// Название зала/площадки (может быть длинным).
    let name: String
    /// Бренд сети для шапки («Доброзал»).
    let brandName: String?
    let address: String
    let phone: String
    let email: String
    let workingHours: String
    let amenities: [String]
    let latitude: Double
    let longitude: Double
    let shopConfig: ClubShopConfig?
    let appUpdate: AppUpdateInfo?
    let network: ClubNetworkInfo?

    /// Бренд для UI: `brand_name`, иначе fallback на «Доброзал».
    var resolvedBrandName: String {
        var brand = brandName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if brand.isEmpty || brand == "FitnessClub" {
            brand = AppConfiguration.appDisplayName
        }
        return brand
    }

    enum CodingKeys: String, CodingKey {
        case id, promoTitle, promoSubtitle, name, brandName, address, phone, email
        case workingHours, amenities, latitude, longitude, shopConfig, appUpdate, network
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(String.self, forKey: .id)
        promoTitle = try c.decodeIfPresent(String.self, forKey: .promoTitle)
        promoSubtitle = try c.decodeIfPresent(String.self, forKey: .promoSubtitle)
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        brandName = try c.decodeIfPresent(String.self, forKey: .brandName)
        address = (try? c.decode(String.self, forKey: .address)) ?? ""
        phone = (try? c.decode(String.self, forKey: .phone)) ?? ""
        email = (try? c.decode(String.self, forKey: .email)) ?? ""
        workingHours = (try? c.decode(String.self, forKey: .workingHours)) ?? ""
        amenities = (try? c.decode([String].self, forKey: .amenities)) ?? []
        latitude = (try? c.decode(Double.self, forKey: .latitude)) ?? 0
        longitude = (try? c.decode(Double.self, forKey: .longitude)) ?? 0
        shopConfig = try c.decodeIfPresent(ClubShopConfig.self, forKey: .shopConfig)
        appUpdate = try c.decodeIfPresent(AppUpdateInfo.self, forKey: .appUpdate)
        network = try c.decodeIfPresent(ClubNetworkInfo.self, forKey: .network)
    }
}

/// Карусель промо (`HomeScreen.kt` / GET `club/promotions`).
struct ClubPromotion: Codable, Hashable, Sendable {
    let id: String
    let title: String
    let subtitle: String?
    let imageUrl: String?
    let buttonText: String?
    let actionType: String?
    let actionValue: String?
    let bgFrom: String?
    let bgTo: String?
    let sortOrder: Int?

    var resolvedButtonText: String {
        let t = buttonText?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return t.isEmpty ? "Подробнее" : t
    }

    /// Как значение по умолчанию у `ClubPromotion.actionType` в Android.
    var resolvedActionType: String {
        let t = actionType?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return t.isEmpty ? "shop" : t
    }

    enum CodingKeys: String, CodingKey {
        case id, title, subtitle, imageUrl, buttonText, actionType, actionValue, bgFrom, bgTo, sortOrder
    }

    init(
        id: String,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        buttonText: String?,
        actionType: String?,
        actionValue: String?,
        bgFrom: String?,
        bgTo: String?,
        sortOrder: Int?
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.imageUrl = imageUrl
        self.buttonText = buttonText
        self.actionType = actionType
        self.actionValue = actionValue
        self.bgFrom = bgFrom
        self.bgTo = bgTo
        self.sortOrder = sortOrder
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        title = (try? c.decode(String.self, forKey: .title)) ?? ""
        subtitle = try c.decodeIfPresent(String.self, forKey: .subtitle)
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl)
        buttonText = try c.decodeIfPresent(String.self, forKey: .buttonText)
        actionType = try c.decodeIfPresent(String.self, forKey: .actionType)
        actionValue = try c.decodeIfPresent(String.self, forKey: .actionValue)
        bgFrom = try c.decodeIfPresent(String.self, forKey: .bgFrom)
        bgTo = try c.decodeIfPresent(String.self, forKey: .bgTo)
        sortOrder = try c.decodeIfPresent(Int.self, forKey: .sortOrder)
    }
}

struct GymOccupancy: Codable, Sendable {
    let current: Int
    let maxCapacity: Int
    let percentage: Int
    let status: String

    enum CodingKeys: String, CodingKey {
        case current, maxCapacity, percentage, status
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        current = try c.decodeIfPresent(Int.self, forKey: .current) ?? 0
        maxCapacity = try c.decodeIfPresent(Int.self, forKey: .maxCapacity) ?? 0
        percentage = try c.decodeIfPresent(Int.self, forKey: .percentage) ?? 0
        status = (try? c.decode(String.self, forKey: .status)) ?? "ok"
    }
}

struct AccessStatus: Codable, Sendable {
    var isInside: Bool
    var clubId: Int?
    var lastEntryAt: String?
    var lastExitAt: String?

    static let outside = AccessStatus(isInside: false, clubId: nil, lastEntryAt: nil, lastExitAt: nil)

    enum CodingKeys: String, CodingKey {
        case isInside, clubId, lastEntryAt, lastExitAt
    }

    init(isInside: Bool, clubId: Int?, lastEntryAt: String?, lastExitAt: String?) {
        self.isInside = isInside
        self.clubId = clubId
        self.lastEntryAt = lastEntryAt
        self.lastExitAt = lastExitAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isInside = try c.decodeIfPresent(Bool.self, forKey: .isInside) ?? false
        if let i = try? c.decode(Int.self, forKey: .clubId) {
            clubId = i
        } else if let s = try? c.decode(String.self, forKey: .clubId), let i = Int(s) {
            clubId = i
        } else {
            clubId = nil
        }
        lastEntryAt = try c.decodeIfPresent(String.self, forKey: .lastEntryAt)
        lastExitAt = try c.decodeIfPresent(String.self, forKey: .lastExitAt)
    }
}

// MARK: - Notifications

struct ApiNotification: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let type: String
    let title: String
    let message: String
    let createdAt: String
    let isRead: Bool
    let referenceId: String?

    enum CodingKeys: String, CodingKey {
        case id, type, title, message, createdAt, isRead, referenceId
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        type = (try? c.decode(String.self, forKey: .type)) ?? "system"
        title = (try? c.decode(String.self, forKey: .title)) ?? ""
        message = (try? c.decode(String.self, forKey: .message)) ?? ""
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
        isRead = try c.decodeIfPresent(Bool.self, forKey: .isRead) ?? false
        if let s = try? c.decode(String.self, forKey: .referenceId) {
            referenceId = s
        } else if let n = try? c.decode(Int.self, forKey: .referenceId) {
            referenceId = String(n)
        } else {
            referenceId = nil
        }
    }
}

// MARK: - Feedback

struct FeedbackRequest: Codable, Sendable {
    let rating: Int
    let comment: String
    let type: String
    let referenceId: String?
}

struct FeedbackResponse: Codable, Sendable {
    let success: Bool
    let id: String?

    enum CodingKeys: String, CodingKey {
        case success, id
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = try c.decodeIfPresent(Bool.self, forKey: .success) ?? true
        if let s = try? c.decode(String.self, forKey: .id) {
            id = s
        } else if let n = try? c.decode(Int.self, forKey: .id) {
            id = String(n)
        } else {
            id = nil
        }
    }
}

// MARK: - Support (`HelpScreen.kt` → POST `support/tickets`)

struct SupportTicketRequest: Codable, Sendable {
    let subject: String
    let message: String
    let category: String
    let contactEmail: String?
}

struct SupportTicketCreateResponse: Codable, Sendable {
    let success: Bool?
    let id: String?

    enum CodingKeys: String, CodingKey {
        case success, id
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        success = try c.decodeIfPresent(Bool.self, forKey: .success)
        if let s = try? c.decode(String.self, forKey: .id) {
            id = s
        } else if let n = try? c.decode(Int.self, forKey: .id) {
            id = String(n)
        } else {
            id = nil
        }
    }
}

// MARK: - Guest pass

struct GuestPass: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let guestName: String?
    let status: String
    let createdAt: String
    let usedAt: String?
    let qrCodeData: String

    enum CodingKeys: String, CodingKey {
        case id, guestName, status, createdAt, usedAt, qrCodeData
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        guestName = try c.decodeIfPresent(String.self, forKey: .guestName)
        status = (try? c.decode(String.self, forKey: .status)) ?? ""
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
        usedAt = try c.decodeIfPresent(String.self, forKey: .usedAt)
        qrCodeData = (try? c.decode(String.self, forKey: .qrCodeData)) ?? ""
    }
}

struct CreateGuestPassRequest: Codable, Sendable {
    let guestName: String?
}

// MARK: - Lockers

struct Locker: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let number: String
    let status: String

    enum CodingKeys: String, CodingKey {
        case id, number, status
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        if let s = try? c.decode(String.self, forKey: .number) {
            number = s
        } else if let n = try? c.decode(Int.self, forKey: .number) {
            number = String(n)
        } else {
            number = ""
        }
        status = (try? c.decode(String.self, forKey: .status)) ?? "available"
    }
}

struct LockerBooking: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let locker: Locker
    let startedAt: String
    let endsAt: String
    let qrToken: String
    let qrCodeData: String

    enum CodingKeys: String, CodingKey {
        case id, locker, startedAt, endsAt, qrToken, qrCodeData
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        locker = try c.decode(Locker.self, forKey: .locker)
        startedAt = (try? c.decode(String.self, forKey: .startedAt)) ?? ""
        endsAt = (try? c.decode(String.self, forKey: .endsAt)) ?? ""
        qrToken = (try? c.decode(String.self, forKey: .qrToken)) ?? ""
        qrCodeData = (try? c.decode(String.self, forKey: .qrCodeData)) ?? ""
    }
}

// MARK: - Documents

struct ApiDocument: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let category: String?
    let createdAt: String
    let isMine: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, category, createdAt, isMine
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id))
            ?? (try? c.decode(Int.self, forKey: .id)).map(String.init)
            ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? ""
        category = try c.decodeIfPresent(String.self, forKey: .category)
        createdAt = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
        isMine = try c.decodeIfPresent(Bool.self, forKey: .isMine) ?? false
    }
}

// MARK: - Push

struct PushTokenRequest: Codable, Sendable {
    let token: String
    let platform: String
}

// MARK: - Generic success

struct SuccessResponse: Codable, Sendable {
    let success: Bool?
}
