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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    val selectedTrainer: String? = null,
    val error: String? = null
)

@HiltViewModel
class PersonalTrainingViewModel @Inject constructor(
    private val fitnessApi: FitnessApi
) : ViewModel() {
    
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val result = fitnessApi.getTrainings(date = dateStr, type = "personal")
            
            if (result.isSuccessful) {
                val allTrainings = result.body() ?: emptyList()
                val trainings = allTrainings.filter { it.type == TrainingType.PERSONAL }
                val slots = trainings.map { t ->
                    val start = t.startTime.take(16).replace("T", " ").substring(11, 16)
                    val end = t.endTime.take(16).replace("T", " ").substring(11, 16)
                    val trainerName = t.trainer.name
                    TimeSlot(
                        id = t.id,
                        time = "$start-$end",
                        trainerName = trainerName,
                        trainingType = t.name,
                        room = t.room,
                        isAvailable = !t.isBooked && t.spotsLeft > 0
                    )
                }
                _uiState.update {
                    it.copy(isLoading = false, timeSlots = slots)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        timeSlots = emptyList(),
                        error = result.message() ?: "Ошибка загрузки"
                    )
                }
            }
        }
    }
    
    fun bookSlot(slot: TimeSlot) {
        // TODO: Implement booking via fitnessApi.bookTraining(slot.id)
    }

    fun retryLoad() {
        loadTimeSlots()
    }
}
