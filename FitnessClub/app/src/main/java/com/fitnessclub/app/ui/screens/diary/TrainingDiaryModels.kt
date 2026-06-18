package com.fitnessclub.app.ui.screens.diary

import com.fitnessclub.app.data.local.DiaryExercise
import com.fitnessclub.app.data.local.TrainingDiaryEntry

enum class DiaryTab(val label: String) {
    JOURNAL("Журнал"),
    STATS("Статистика"),
}

data class DiaryDayGroup(
    val dateLabel: String,
    val entries: List<TrainingDiaryEntry>,
)

data class DiaryStatsUi(
    val totalWorkouts: Int = 0,
    val workoutsThisWeek: Int = 0,
    val minutesThisWeek: Int = 0,
    val currentStreak: Int = 0,
    val weeklyActivity: List<DiaryWeekDayUi> = emptyList(),
    val typeBreakdown: List<DiaryTypeStatUi> = emptyList(),
)

data class DiaryWeekDayUi(
    val shortLabel: String,
    val workoutsCount: Int,
    val minutes: Int,
    val isToday: Boolean,
)

data class DiaryTypeStatUi(
    val type: String,
    val label: String,
    val count: Int,
)

data class DraftExerciseUi(
    val id: String,
    val name: String = "",
    val sets: String = "",
    val reps: String = "",
    val weight: String = "",
)

data class DiaryEditorUiState(
    val entryId: String? = null,
    val title: String = "",
    val workoutType: String = WorkoutTypes.CUSTOM,
    val duration: String = "",
    val notes: String = "",
    val exercises: List<DraftExerciseUi> = emptyList(),
    val error: String? = null,
) {
    val isEditing: Boolean get() = entryId != null
}

object WorkoutTypes {
    const val STRENGTH = "strength"
    const val CARDIO = "cardio"
    const val YOGA = "yoga"
    const val PERSONAL = "personal"
    const val CUSTOM = "custom"

    val all = listOf(STRENGTH, CARDIO, YOGA, PERSONAL, CUSTOM)

    fun label(type: String?): String = when (type) {
        STRENGTH -> "Силовая"
        CARDIO -> "Кардио"
        YOGA -> "Йога"
        PERSONAL -> "Персональная"
        else -> "Своя"
    }

    fun templateTitle(type: String): String = when (type) {
        STRENGTH -> "Силовая тренировка"
        CARDIO -> "Кардио"
        YOGA -> "Йога и растяжка"
        PERSONAL -> "Персональная тренировка"
        else -> ""
    }

    fun templateExercises(type: String): List<DraftExerciseUi> = when (type) {
        STRENGTH -> listOf(
            DraftExerciseUi(id = "1", name = "Приседания"),
            DraftExerciseUi(id = "2", name = "Жим лёжа"),
            DraftExerciseUi(id = "3", name = "Тяга"),
        )
        CARDIO -> listOf(
            DraftExerciseUi(id = "1", name = "Беговая дорожка", sets = "1", reps = "20"),
            DraftExerciseUi(id = "2", name = "Велотренажёр", sets = "1", reps = "15"),
        )
        YOGA -> listOf(
            DraftExerciseUi(id = "1", name = "Разминка"),
            DraftExerciseUi(id = "2", name = "Растяжка"),
        )
        else -> emptyList()
    }
}

fun DiaryExercise.summaryLine(): String {
    val parts = buildList {
        if (!name.isBlank()) add(name)
        if (sets != null && reps != null) add("${sets}×$reps")
        else if (reps != null) add("$reps повт.")
        if (weightKg != null) add("${weightKg} кг")
    }
    return parts.joinToString(" · ")
}
