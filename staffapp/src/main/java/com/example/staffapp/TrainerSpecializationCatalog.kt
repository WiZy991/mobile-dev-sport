package com.example.staffapp

/**
 * Справочник специализаций тренера (п.17 репорта).
 * Должен совпадать с StaffOnboardingService::specializationCatalog на бэкенде.
 */
object TrainerSpecializationCatalog {
    const val MAX_SELECTED = 5

    val DEFAULT = listOf(
        "Персональный тренер",
        "Силовые тренировки",
        "Функциональный тренинг",
        "Кроссфит",
        "Йога",
        "Пилатес",
        "Стретчинг",
        "Кардио",
        "Бокс / единоборства",
        "Реабилитация",
        "Похудение",
        "Набор массы",
        "Подготовка к соревнованиям",
        "Детский фитнес",
        "Групповые программы",
    )

    fun parseSelected(raw: String, catalog: List<String> = DEFAULT): List<String> {
        if (raw.isBlank()) return emptyList()
        val lowerCatalog = catalog.map { it.lowercase() }
        return raw.split(',', ';', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { label ->
                val idx = lowerCatalog.indexOf(label.lowercase())
                if (idx >= 0) catalog[idx] else null
            }
            .distinct()
            .take(MAX_SELECTED)
    }

    fun join(selected: List<String>): String = selected.joinToString(", ")
}
