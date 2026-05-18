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
        sberId: String? = nil
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
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        email = try c.decode(String.self, forKey: .email)
        phone = try c.decode(String.self, forKey: .phone)
        name = try c.decode(String.self, forKey: .name)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        bonusPoints = try c.decodeIfPresent(Int.self, forKey: .bonusPoints) ?? 0
        passportVerificationStatus = try c.decodeIfPresent(String.self, forKey: .passportVerificationStatus)
        dateOfBirth = try c.decodeIfPresent(String.self, forKey: .dateOfBirth)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        isVerified = try c.decodeIfPresent(Bool.self, forKey: .isVerified) ?? false
        sberId = try c.decodeIfPresent(String.self, forKey: .sberId)
    }

    enum CodingKeys: String, CodingKey {
        case id
        case email
        case phone
        case name
        case avatarUrl = "avatar_url"
        case bonusPoints = "bonus_points"
        case passportVerificationStatus = "passport_verification_status"
        case dateOfBirth = "date_of_birth"
        case createdAt = "created_at"
        case isVerified = "is_verified"
        case sberId = "sber_id"
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
    }
}

struct AuthResponse: Codable, Sendable {
    let token: String
    let refreshToken: String
    let user: User
}

struct LoginRequest: Codable, Sendable {
    let email: String
    let password: String
}

struct RegisterRequest: Codable, Sendable {
    let email: String
    let password: String
    let phone: String
    let name: String
}

struct UserStats: Codable, Sendable {
    let totalVisits: Int
    let streakDays: Int
    let achievements: [Achievement]
}

struct Achievement: Codable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String
    let unlocked: Bool
}

struct PurchaseItem: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let productName: String
    let quantity: Int
    let price: Double
    let total: Double
    let paymentMethod: String
    let createdAt: String
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
        case id, name, description, type, trainer
        case startTime = "start_time"
        case endTime = "end_time"
        case durationMinutes = "duration_minutes"
        case maxParticipants = "max_participants"
        case currentParticipants = "current_participants"
        case room
        case isBooked = "is_booked"
        case intensity
        case imageUrl = "image_url"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        type = try c.decode(TrainingKind.self, forKey: .type)
        trainer = try c.decode(Trainer.self, forKey: .trainer)
        startTime = try c.decode(String.self, forKey: .startTime)
        endTime = try c.decode(String.self, forKey: .endTime)
        if let v = try? c.decode(Int.self, forKey: .durationMinutes) {
            durationMinutes = v
        } else {
            let d = try c.decode(Double.self, forKey: .durationMinutes)
            durationMinutes = Int(d)
        }
        maxParticipants = try c.decode(Int.self, forKey: .maxParticipants)
        currentParticipants = try c.decode(Int.self, forKey: .currentParticipants)
        room = try c.decode(String.self, forKey: .room)
        isBooked = try c.decodeIfPresent(Bool.self, forKey: .isBooked) ?? false
        intensity = try c.decodeIfPresent(TrainingIntensity.self, forKey: .intensity)
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
}

struct Subscription: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let type: SubscriptionKind
    let startDate: String
    let endDate: String
    let status: SubscriptionState
    let visitsTotal: Int?
    let visitsUsed: Int
    let freezeDaysTotal: Int
    let freezeDaysUsed: Int
    let isFrozen: Bool
    let price: Double
    let description: String?

    var visitsLeft: Int? {
        guard let t = visitsTotal else { return nil }
        return t - visitsUsed
    }

    var freezeDaysLeft: Int { freezeDaysTotal - freezeDaysUsed }
}

struct SubscriptionPlan: Codable, Hashable, Sendable {
    let id: String?
    let name: String?
    let description: String?
    let price: Double
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

// MARK: - Products / Shop

struct Product: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let description: String?
    let price: Double
    let category: String
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
}

struct ClubInfo: Codable, Hashable, Sendable {
    let id: String?
    let promoTitle: String?
    let promoSubtitle: String?
    let name: String
    let address: String
    let phone: String
    let email: String
    let workingHours: String
    let amenities: [String]
    let latitude: Double
    let longitude: Double
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
}

struct GymOccupancy: Codable, Sendable {
    let current: Int
    let maxCapacity: Int
    let percentage: Int
    let status: String
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
    let id: Int?
}

// MARK: - Guest pass

struct GuestPass: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let guestName: String?
    let status: String
    let createdAt: String
    let usedAt: String?
    let qrCodeData: String
}

struct CreateGuestPassRequest: Codable, Sendable {
    let guestName: String?
}

// MARK: - Lockers

struct Locker: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let number: String
    let status: String
}

struct LockerBooking: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let locker: Locker
    let startedAt: String
    let endsAt: String
    let qrToken: String
    let qrCodeData: String
}

// MARK: - Documents

struct ApiDocument: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let category: String?
    let createdAt: String
    let isMine: Bool
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
