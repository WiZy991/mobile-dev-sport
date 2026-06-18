package com.fitnessclub.app.data.catalog

import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.model.SubscriptionType

/**
 * Актуальные цены и сроки абонементов (магазин и экран покупки).
 * Идентификаторы согласованы с mock API; на бэкенде должны существовать планы с теми же id.
 */
object LocalSubscriptionCatalog {
    /** Id в формате `plan-{число}` совпадает с `subscription_plans.id` на сервере после миграции. */
    val PLANS: List<SubscriptionPlan> = listOf(
        SubscriptionPlan(
            id = "plan-1",
            name = "На 12 месяцев",
            description = "Неограниченное посещение. Заморозка: +30 дней.",
            price = 38_000.0,
            durationDays = 365,
            visitsCount = null,
            type = SubscriptionType.UNLIMITED,
            features = listOf("Тренажёрный зал", "Групповые программы", "Заморозка +30 дней"),
            isPopular = true,
        ),
        SubscriptionPlan(
            id = "plan-2",
            name = "На 6 месяцев",
            description = "Неограниченное посещение. Заморозка: +20 дней.",
            price = 25_000.0,
            durationDays = 180,
            visitsCount = null,
            type = SubscriptionType.UNLIMITED,
            features = listOf("Тренажёрный зал", "Групповые программы", "Заморозка +20 дней"),
            isPopular = false,
        ),
        SubscriptionPlan(
            id = "plan-3",
            name = "На 4 месяца",
            description = "Неограниченное посещение. Заморозка: +14 дней.",
            price = 18_000.0,
            durationDays = 120,
            visitsCount = null,
            type = SubscriptionType.UNLIMITED,
            features = listOf("Тренажёрный зал", "Групповые программы", "Заморозка +14 дней"),
            isPopular = false,
        ),
        SubscriptionPlan(
            id = "plan-4",
            name = "На 3 месяца",
            description = "Неограниченное посещение. Заморозка: +14 дней.",
            price = 16_500.0,
            durationDays = 90,
            visitsCount = null,
            type = SubscriptionType.UNLIMITED,
            features = listOf("Тренажёрный зал", "Групповые программы", "Заморозка +14 дней"),
            isPopular = false,
        ),
        SubscriptionPlan(
            id = "plan-5",
            name = "На 1 месяц",
            description = "Неограниченное посещение.",
            price = 6_000.0,
            durationDays = 30,
            visitsCount = null,
            type = SubscriptionType.UNLIMITED,
            features = listOf("Тренажёрный зал", "Групповые программы"),
            isPopular = false,
        ),
        SubscriptionPlan(
            id = "plan-6",
            name = "Разовое посещение",
            description = "Один визит в зал.",
            price = 990.0,
            durationDays = null,
            visitsCount = 1,
            type = SubscriptionType.LIMITED,
            features = listOf("Одно посещение"),
            isPopular = false,
        ),
    )

    /** Как в прайс-листе (сторис): 12 → 6 → 3 → 4 → 1 мес → разовое. */
    val PLANS_PRICELIST_ORDER: List<SubscriptionPlan> = listOf(
        PLANS[0],
        PLANS[1],
        PLANS[3],
        PLANS[2],
        PLANS[4],
        PLANS[5],
    )

    /** Дней заморозки по тарифу; null — заморозка недоступна. */
    fun freezeDaysForPlan(planId: String): Int? = when (planId) {
        "plan-1" -> 30
        "plan-2" -> 20
        "plan-3", "plan-4" -> 14
        else -> null
    }

    fun freezeSubtitleForPlan(planId: String): String? =
        freezeDaysForPlan(planId)?.let { "+$it дней заморозки" }
}
