import Foundation

/// Актуальные цены и сроки абонементов — как `LocalSubscriptionCatalog.kt` на Android.
enum LocalSubscriptionCatalog {
    static let plans: [SubscriptionPlan] = [
        SubscriptionPlan(
            id: "plan-1",
            name: "На 12 месяцев",
            description: "Неограниченное посещение. Заморозка: +30 дней.",
            price: 38_000,
            durationDays: 365,
            visitsCount: nil,
            type: .unlimited,
            features: ["Тренажёрный зал", "Групповые программы", "Заморозка +30 дней"],
            isPopular: true
        ),
        SubscriptionPlan(
            id: "plan-2",
            name: "На 6 месяцев",
            description: "Неограниченное посещение. Заморозка: +20 дней.",
            price: 25_000,
            durationDays: 180,
            visitsCount: nil,
            type: .unlimited,
            features: ["Тренажёрный зал", "Групповые программы", "Заморозка +20 дней"],
            isPopular: false
        ),
        SubscriptionPlan(
            id: "plan-3",
            name: "На 4 месяца",
            description: "Неограниченное посещение. Заморозка: +15 дней.",
            price: 18_000,
            durationDays: 120,
            visitsCount: nil,
            type: .unlimited,
            features: ["Тренажёрный зал", "Групповые программы", "Заморозка +15 дней"],
            isPopular: false
        ),
        SubscriptionPlan(
            id: "plan-4",
            name: "На 3 месяца",
            description: "Неограниченное посещение. Заморозка: +14 дней.",
            price: 16_500,
            durationDays: 90,
            visitsCount: nil,
            type: .unlimited,
            features: ["Тренажёрный зал", "Групповые программы", "Заморозка +14 дней"],
            isPopular: false
        ),
        SubscriptionPlan(
            id: "plan-5",
            name: "На 1 месяц",
            description: "Неограниченное посещение.",
            price: 6_000,
            durationDays: 30,
            visitsCount: nil,
            type: .unlimited,
            features: ["Тренажёрный зал", "Групповые программы"],
            isPopular: false
        ),
        SubscriptionPlan(
            id: "plan-6",
            name: "Разовое посещение",
            description: "Один визит в зал.",
            price: 990,
            durationDays: nil,
            visitsCount: 1,
            type: .limited,
            features: ["Одно посещение"],
            isPopular: false
        ),
    ]

    /// Как в прайс-листе регистрации: 12 → 6 → 3 → 4 → 1 мес → разовое.
    static let pricelistOrder: [SubscriptionPlan] = [plans[0], plans[1], plans[3], plans[2], plans[4], plans[5]]

    static func freezeSubtitle(planId: String) -> String? {
        switch planId {
        case "plan-1": return "+30 дней заморозки"
        case "plan-2": return "+20 дней заморозки"
        case "plan-4": return "+14 дней заморозки"
        case "plan-3": return "+15 дней заморозки"
        default: return nil
        }
    }
}
