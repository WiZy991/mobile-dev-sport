package com.fitnessclub.app.ui.screens.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import javax.inject.Inject

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
    val weekDays: List<DayInfo> = emptyList(),
    val timeSlots: List<TimeSlot> = emptyList(),
    val selectedTrainingType: String? = null,
    val selectedTrainer: String? = null
)

@HiltViewModel
class PersonalTrainingViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(PersonalTrainingUiState())
    val uiState: StateFlow<PersonalTrainingUiState> = _uiState.asStateFlow()
    
    private var currentWeekStart = LocalDate.now()
    private var selectedDate = LocalDate.now()
    
    init {
        loadWeek()
        loadTimeSlots()
    }
    
    private fun loadWeek() {
        val days = (0..6).map { offset ->
            val date = currentWeekStart.plusDays(offset.toLong())
            DayInfo(
                date = date,
                dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).lowercase(),
                dayOfMonth = date.dayOfMonth,
                isSelected = date == selectedDate,
                isWeekend = date.dayOfWeek.value >= 6
            )
        }
        
        val monthName = currentWeekStart.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        val year = currentWeekStart.year
        
        _uiState.update {
            it.copy(
                currentMonth = "$monthName $year г.",
                weekDays = days
            )
        }
    }
    
    fun selectDay(day: DayInfo) {
        selectedDate = day.date
        loadWeek()
        loadTimeSlots()
    }
    
    fun previousWeek() {
        currentWeekStart = currentWeekStart.minusWeeks(1)
        loadWeek()
        loadTimeSlots()
    }
    
    fun nextWeek() {
        currentWeekStart = currentWeekStart.plusWeeks(1)
        loadWeek()
        loadTimeSlots()
    }
    
    private fun loadTimeSlots() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Mock data
            val slots = listOf(
                TimeSlot(
                    id = "1",
                    time = "9:00-10:00",
                    trainerName = "Петров Алексей",
                    trainingType = "Йога тренировка",
                    room = "Зал йоги"
                ),
                TimeSlot(
                    id = "2",
                    time = "9:00-10:00",
                    trainerName = "Сотников Иван Иванович",
                    trainingType = "Йога тренировка",
                    room = "Зал йоги"
                ),
                TimeSlot(
                    id = "3",
                    time = "10:00-11:00",
                    trainerName = "Сотников Иван Иванович",
                    trainingType = "Йога тренировка",
                    room = "Зал йоги",
                    isAvailable = false
                ),
                TimeSlot(
                    id = "4",
                    time = "11:00-12:00",
                    trainerName = "Петров Алексей",
                    trainingType = "Йога тренировка",
                    room = "Зал йоги"
                ),
                TimeSlot(
                    id = "5",
                    time = "11:00-12:00",
                    trainerName = "Сотников Иван Иванович",
                    trainingType = "Йога тренировка",
                    room = "Зал йоги"
                ),
                TimeSlot(
                    id = "6",
                    time = "12:00-13:00",
                    trainerName = "Петров Алексей",
                    trainingType = "Силовая тренировка",
                    room = "Тренажёрный зал"
                ),
                TimeSlot(
                    id = "7",
                    time = "14:00-15:00",
                    trainerName = "Волков Дмитрий",
                    trainingType = "Бокс",
                    room = "Зал единоборств"
                ),
                TimeSlot(
                    id = "8",
                    time = "15:00-16:00",
                    trainerName = "Козлова Ольга",
                    trainingType = "Пилатес",
                    room = "Зал пилатеса"
                )
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    timeSlots = slots
                )
            }
        }
    }
    
    fun bookSlot(slot: TimeSlot) {
        // TODO: Implement booking
    }
}
