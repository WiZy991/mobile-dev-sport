package com.fitnessclub.app.ui.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.local.DiaryExercise
import com.fitnessclub.app.data.local.NewTrainingDiaryEntry
import com.fitnessclub.app.data.local.TrainingDiaryEntry
import com.fitnessclub.app.data.local.TrainingDiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class TrainingDiaryUiState(
    val entries: List<TrainingDiaryEntry> = emptyList(),
    val selectedTab: DiaryTab = DiaryTab.JOURNAL,
    val stats: DiaryStatsUi = DiaryStatsUi(),
    val groupedEntries: List<DiaryDayGroup> = emptyList(),
    val editor: DiaryEditorUiState? = null,
    val deleteCandidateId: String? = null,
)

@HiltViewModel
class TrainingDiaryViewModel @Inject constructor(
    private val diaryRepository: TrainingDiaryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrainingDiaryUiState())
    val uiState: StateFlow<TrainingDiaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            diaryRepository.observeEntries().collect { items ->
                _uiState.update {
                    it.copy(
                        entries = items,
                        stats = computeStats(items),
                        groupedEntries = groupByDay(items),
                    )
                }
            }
        }
    }

    fun selectTab(tab: DiaryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun openNewWorkout(templateType: String? = null) {
        val type = templateType ?: WorkoutTypes.CUSTOM
        val exercises = WorkoutTypes.templateExercises(type).ifEmpty {
            listOf(DraftExerciseUi(id = UUID.randomUUID().toString()))
        }
        _uiState.update {
            it.copy(
                editor = DiaryEditorUiState(
                    title = WorkoutTypes.templateTitle(type),
                    workoutType = type,
                    exercises = exercises,
                ),
            )
        }
    }

    fun openEditWorkout(entry: TrainingDiaryEntry) {
        _uiState.update {
            it.copy(
                editor = DiaryEditorUiState(
                    entryId = entry.id,
                    title = entry.title,
                    workoutType = entry.workoutType ?: WorkoutTypes.CUSTOM,
                    duration = entry.durationMinutes?.toString().orEmpty(),
                    notes = entry.notes,
                    exercises = entry.exercises.orEmpty().map { exercise ->
                        DraftExerciseUi(
                            id = UUID.randomUUID().toString(),
                            name = exercise.name,
                            sets = exercise.sets?.toString().orEmpty(),
                            reps = exercise.reps?.toString().orEmpty(),
                            weight = exercise.weightKg?.let { w ->
                                if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()
                            }.orEmpty(),
                        )
                    }.ifEmpty { listOf(DraftExerciseUi(id = UUID.randomUUID().toString())) },
                ),
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    fun onEditorTitleChanged(value: String) = updateEditor { it.copy(title = value, error = null) }

    fun onEditorDurationChanged(value: String) {
        updateEditor { it.copy(duration = value.filter { ch -> ch.isDigit() }.take(3), error = null) }
    }

    fun onEditorNotesChanged(value: String) = updateEditor { it.copy(notes = value, error = null) }

    fun onEditorTypeSelected(type: String) {
        updateEditor {
            it.copy(
                workoutType = type,
                title = if (it.title.isBlank() || WorkoutTypes.all.any { t -> it.title == WorkoutTypes.templateTitle(t) }) {
                    WorkoutTypes.templateTitle(type)
                } else {
                    it.title
                },
                error = null,
            )
        }
    }

    fun addExerciseRow() {
        updateEditor {
            it.copy(
                exercises = it.exercises + DraftExerciseUi(id = UUID.randomUUID().toString()),
                error = null,
            )
        }
    }

    fun removeExerciseRow(id: String) {
        updateEditor {
            val next = it.exercises.filterNot { row -> row.id == id }
            it.copy(exercises = next.ifEmpty { listOf(DraftExerciseUi(id = UUID.randomUUID().toString())) }, error = null)
        }
    }

    fun onExerciseNameChanged(id: String, value: String) = updateExercise(id) { it.copy(name = value) }

    fun onExerciseSetsChanged(id: String, value: String) {
        updateExercise(id) { it.copy(sets = value.filter { ch -> ch.isDigit() }.take(2)) }
    }

    fun onExerciseRepsChanged(id: String, value: String) {
        updateExercise(id) { it.copy(reps = value.filter { ch -> ch.isDigit() }.take(3)) }
    }

    fun onExerciseWeightChanged(id: String, value: String) {
        val cleaned = value.filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
            .take(6)
        updateExercise(id) { it.copy(weight = cleaned) }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val title = editor.title.trim()
        if (title.isBlank()) {
            updateEditor { it.copy(error = "Введите название тренировки") }
            return
        }
        val payload = NewTrainingDiaryEntry(
            dateMillis = System.currentTimeMillis(),
            title = title,
            durationMinutes = editor.duration.toIntOrNull(),
            notes = editor.notes.trim(),
            workoutType = editor.workoutType,
            exercises = editor.exercises
                .filter { it.name.isNotBlank() }
                .map { row ->
                    DiaryExercise(
                        name = row.name.trim(),
                        sets = row.sets.toIntOrNull(),
                        reps = row.reps.toIntOrNull(),
                        weightKg = row.weight.replace(',', '.').toDoubleOrNull(),
                    )
                },
        )
        viewModelScope.launch {
            if (editor.entryId == null) {
                diaryRepository.addEntry(payload)
            } else {
                diaryRepository.updateEntry(editor.entryId, payload)
            }
            closeEditor()
        }
    }

    fun requestDelete(entryId: String) {
        _uiState.update { it.copy(deleteCandidateId = entryId) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleteCandidateId = null) }
    }

    fun confirmDelete() {
        val id = _uiState.value.deleteCandidateId ?: return
        viewModelScope.launch {
            diaryRepository.deleteEntry(id)
            _uiState.update { it.copy(deleteCandidateId = null) }
        }
    }

    private inline fun updateEditor(block: (DiaryEditorUiState) -> DiaryEditorUiState) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = block(editor))
        }
    }

    private inline fun updateExercise(id: String, block: (DraftExerciseUi) -> DraftExerciseUi) {
        updateEditor { editor ->
            editor.copy(
                exercises = editor.exercises.map { row ->
                    if (row.id == id) block(row) else row
                },
                error = null,
            )
        }
    }

    private fun groupByDay(entries: List<TrainingDiaryEntry>): List<DiaryDayGroup> {
        if (entries.isEmpty()) return emptyList()
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("ru", "RU"))
        return entries
            .groupBy { entry -> entry.dateMillis.toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayEntries) ->
                DiaryDayGroup(
                    dateLabel = date.format(formatter).replaceFirstChar { it.titlecase(Locale("ru", "RU")) },
                    entries = dayEntries.sortedByDescending { it.dateMillis },
                )
            }
    }

    private fun computeStats(entries: List<TrainingDiaryEntry>): DiaryStatsUi {
        if (entries.isEmpty()) return DiaryStatsUi()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.minusDays(6)

        val weekEntries = entries.filter { entry ->
            val date = entry.dateMillis.toLocalDate()
            !date.isBefore(weekStart) && !date.isAfter(today)
        }

        val weeklyActivity = (0..6).map { offset ->
            val day = weekStart.plusDays(offset.toLong())
            val dayEntries = entries.filter { it.dateMillis.toLocalDate() == day }
            DiaryWeekDayUi(
                shortLabel = day.format(DateTimeFormatter.ofPattern("EE", Locale("ru", "RU"))),
                workoutsCount = dayEntries.size,
                minutes = dayEntries.sumOf { it.durationMinutes ?: 0 },
                isToday = day == today,
            )
        }

        val typeBreakdown = WorkoutTypes.all
            .mapNotNull { type ->
                val count = entries.count { (it.workoutType ?: WorkoutTypes.CUSTOM) == type }
                if (count == 0) null else DiaryTypeStatUi(type, WorkoutTypes.label(type), count)
            }
            .sortedByDescending { it.count }

        return DiaryStatsUi(
            totalWorkouts = entries.size,
            workoutsThisWeek = weekEntries.size,
            minutesThisWeek = weekEntries.sumOf { it.durationMinutes ?: 0 },
            currentStreak = calculateStreak(entries, zone),
            weeklyActivity = weeklyActivity,
            typeBreakdown = typeBreakdown,
        )
    }

    private fun calculateStreak(entries: List<TrainingDiaryEntry>, zone: ZoneId): Int {
        val workoutDays = entries.map { it.dateMillis.toLocalDate() }.toSet()
        var streak = 0
        var day = LocalDate.now(zone)
        if (day !in workoutDays) day = day.minusDays(1)
        while (day in workoutDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
