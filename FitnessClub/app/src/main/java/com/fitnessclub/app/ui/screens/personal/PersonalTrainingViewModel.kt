package com.fitnessclub.app.ui.screens.personal

import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.TrainingType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

enum class CalendarMode { WEEK, MONTH }

data class DayInfo(
    val date: LocalDate,
    val dayOfWeek: String,
    val dayOfMonth: Int,
    val isSelected: Boolean,
    val isWeekend: Boolean
)

data class PersonalTrainingUiState(
    val isLoading: Boolean = false,
    val currentMonth: String = "",
    val calendarMode: CalendarMode = CalendarMode.WEEK,
    val weekDays: List<DayInfo> = emptyList(),
    val timeSlots: List<TimeSlot> = emptyList(),
    val trainingTypeOptions: List<String> = emptyList(),
    val trainerNameOptions: List<String> = emptyList(),
    val selectedTrainingType: String? = null,
    val selectedTrainer: String? = null,
    val error: String? = null,
    val bookSuccessMessage: String? = null,
)

@HiltViewModel
class PersonalTrainingViewModel @Inject constructor(
    private val fitnessApi: FitnessApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalTrainingUiState())
    val uiState: StateFlow<PersonalTrainingUiState> = _uiState.asStateFlow()

    private var currentWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private var visibleMonth: YearMonth = YearMonth.from(LocalDate.now())
    private var selectedDate: LocalDate = LocalDate.now()
    private var loadedSlots: List<TimeSlot> = emptyList()

    init {
        refreshCalendarStrip()
        loadTimeSlots()
    }

    private fun refreshCalendarStrip() {
        val mode = _uiState.value.calendarMode
        val days: List<LocalDate> = when (mode) {
            CalendarMode.WEEK -> (0..6).map { currentWeekStart.plusDays(it.toLong()) }
            CalendarMode.MONTH -> {
                val first = visibleMonth.atDay(1)
                val len = visibleMonth.lengthOfMonth()
                (0 until len).map { first.plusDays(it.toLong()) }
            }
        }

        val dayInfos = days.map { date ->
            DayInfo(
                date = date,
                dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).lowercase(),
                dayOfMonth = date.dayOfMonth,
                isSelected = date == selectedDate,
                isWeekend = date.dayOfWeek.value >= 6
            )
        }

        // Неделя на стыке двух месяцев: заголовок по понедельнику давал «март», хотя выбрана дата в апреле.
        val titleDate = when (mode) {
            CalendarMode.WEEK -> selectedDate
            CalendarMode.MONTH -> visibleMonth.atDay(1)
        }
        val monthName = titleDate.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        val year = titleDate.year

        _uiState.update {
            it.copy(
                currentMonth = "$monthName $year г.",
                calendarMode = mode,
                weekDays = dayInfos
            )
        }
    }

    fun selectDay(day: DayInfo) {
        selectedDate = day.date
        refreshCalendarStrip()
        loadTimeSlots()
    }

    fun toggleCalendarMode() {
        val newMode = if (_uiState.value.calendarMode == CalendarMode.WEEK) CalendarMode.MONTH else CalendarMode.WEEK
        _uiState.update { it.copy(calendarMode = newMode) }
        if (newMode == CalendarMode.MONTH) {
            visibleMonth = YearMonth.from(selectedDate)
        } else {
            currentWeekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        refreshCalendarStrip()
        loadTimeSlots()
    }

    fun previousPeriod() {
        when (_uiState.value.calendarMode) {
            CalendarMode.WEEK -> {
                currentWeekStart = currentWeekStart.minusWeeks(1)
                val end = currentWeekStart.plusDays(6)
                if (selectedDate.isBefore(currentWeekStart) || selectedDate.isAfter(end)) {
                    selectedDate = currentWeekStart
                }
            }
            CalendarMode.MONTH -> {
                visibleMonth = visibleMonth.minusMonths(1)
                val first = visibleMonth.atDay(1)
                val last = visibleMonth.atEndOfMonth()
                if (selectedDate.isBefore(first) || selectedDate.isAfter(last)) {
                    selectedDate = first
                }
            }
        }
        refreshCalendarStrip()
        loadTimeSlots()
    }

    fun nextPeriod() {
        when (_uiState.value.calendarMode) {
            CalendarMode.WEEK -> {
                currentWeekStart = currentWeekStart.plusWeeks(1)
                val end = currentWeekStart.plusDays(6)
                if (selectedDate.isBefore(currentWeekStart) || selectedDate.isAfter(end)) {
                    selectedDate = currentWeekStart
                }
            }
            CalendarMode.MONTH -> {
                visibleMonth = visibleMonth.plusMonths(1)
                val first = visibleMonth.atDay(1)
                val last = visibleMonth.atEndOfMonth()
                if (selectedDate.isBefore(first) || selectedDate.isAfter(last)) {
                    selectedDate = first
                }
            }
        }
        refreshCalendarStrip()
        loadTimeSlots()
    }

    fun setTrainingTypeFilter(value: String?) {
        _uiState.update { s ->
            s.copy(
                selectedTrainingType = value,
                timeSlots = filterSlots(loadedSlots, value, s.selectedTrainer)
            )
        }
    }

    fun setTrainerFilter(value: String?) {
        _uiState.update { s ->
            s.copy(
                selectedTrainer = value,
                timeSlots = filterSlots(loadedSlots, s.selectedTrainingType, value)
            )
        }
    }

    private fun filterSlots(slots: List<TimeSlot>, type: String?, trainer: String?): List<TimeSlot> =
        slots.filter { slot ->
            val typeOk = type == null || slot.trainingType == type
            val trainerOk = trainer == null || slot.trainerName == trainer
            typeOk && trainerOk
        }

    private fun loadTimeSlots() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val result = fitnessApi.getTrainings(date = dateStr, type = "personal")

            if (result.isSuccessful) {
                val allTrainings = result.body() ?: emptyList()
                val trainings = allTrainings.filter { it.type == TrainingType.PERSONAL }
                loadedSlots = trainings.map { t ->
                    val start = extractTimeHm(t.startTime)
                    val end = extractTimeHm(t.endTime)
                    TimeSlot(
                        id = t.id,
                        time = "$start-$end",
                        trainerName = t.trainer.name.ifBlank { "Без тренера" },
                        trainingType = t.name,
                        room = t.room,
                        isAvailable = !t.isBooked && t.spotsLeft > 0
                    )
                }
                val types = loadedSlots.map { it.trainingType }.distinct().sorted()
                val trainers = loadedSlots.map { it.trainerName }.distinct().sorted()
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        trainingTypeOptions = types,
                        trainerNameOptions = trainers,
                        timeSlots = filterSlots(loadedSlots, s.selectedTrainingType, s.selectedTrainer)
                    )
                }
            } else {
                loadedSlots = emptyList()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        timeSlots = emptyList(),
                        trainingTypeOptions = emptyList(),
                        trainerNameOptions = emptyList(),
                        error = result.message() ?: "Ошибка загрузки"
                    )
                }
            }
        }
    }

    fun bookSlot(slot: TimeSlot) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, bookSuccessMessage = null) }
            try {
                val res = fitnessApi.bookTraining(slot.id)
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bookSuccessMessage = "Вы записаны на ${slot.time} (${slot.trainerName})"
                        )
                    }
                    loadTimeSlots()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = res.errorBody()?.string()?.take(200) ?: "Не удалось записаться"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка сети")
                }
            }
        }
    }

    fun consumeBookSuccess() {
        _uiState.update { it.copy(bookSuccessMessage = null) }
    }

    fun retryLoad() {
        loadTimeSlots()
    }

    /** HH:mm из ISO `YYYY-MM-DDTHH:mm:ss` без падений по индексам. */
    private fun extractTimeHm(iso: String): String {
        if (iso.isBlank()) return "??:??"
        val t = iso.indexOf('T')
        val from = if (t >= 0) t + 1 else 0
        val to = from + 5
        return if (to <= iso.length) iso.substring(from, to) else "??:??"
    }
}
