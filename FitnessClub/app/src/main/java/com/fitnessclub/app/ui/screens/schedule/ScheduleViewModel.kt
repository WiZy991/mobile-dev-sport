package com.fitnessclub.app.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.Training
import com.fitnessclub.app.data.model.TrainingType
import com.fitnessclub.app.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val trainingRepository: TrainingRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    
    private val dateFormatter = DateTimeFormatter.ISO_DATE
    
    init {
        loadTrainings()
    }
    
    fun loadTrainings() {
        viewModelScope.launch {
            val dateString = _uiState.value.selectedDate.format(dateFormatter)
            val typeFilter = _uiState.value.selectedFilter?.name?.lowercase()
            
            trainingRepository.getTrainings(dateString, typeFilter).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            trainings = result.data,
                            error = null
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }
    
    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadTrainings()
    }
    
    fun selectFilter(filter: TrainingType?) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        loadTrainings()
    }
    
    fun refresh() {
        loadTrainings()
    }
}

data class ScheduleUiState(
    val trainings: List<Training> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedFilter: TrainingType? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
