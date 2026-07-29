package com.fitnessclub.app.ui.screens.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.Booking
import com.fitnessclub.app.data.model.Training
import com.fitnessclub.app.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrainingDetailsViewModel @Inject constructor(
    private val trainingRepository: TrainingRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TrainingDetailsUiState())
    val uiState: StateFlow<TrainingDetailsUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<TrainingDetailsEvent>()
    val events: SharedFlow<TrainingDetailsEvent> = _events.asSharedFlow()
    
    fun loadTraining(trainingId: String) {
        viewModelScope.launch {
            trainingRepository.getTrainingDetails(trainingId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            training = result.data,
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
    
    fun bookTraining() {
        val training = _uiState.value.training ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBooking = true)
            
            trainingRepository.bookTraining(training.safeId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isBooking = false,
                            training = training.copy(isBooked = true)
                        )
                        _events.emit(TrainingDetailsEvent.BookingSuccess)
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isBooking = false,
                            error = result.message
                        )
                        _events.emit(TrainingDetailsEvent.BookingError(result.message))
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }
    
    fun joinWaitingList() {
        val training = _uiState.value.training ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBooking = true)
            
            trainingRepository.joinWaitingList(training.safeId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(isBooking = false)
                        _events.emit(TrainingDetailsEvent.WaitingListSuccess)
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isBooking = false,
                            error = result.message
                        )
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }
}

data class TrainingDetailsUiState(
    val training: Training? = null,
    val isLoading: Boolean = false,
    val isBooking: Boolean = false,
    val error: String? = null
)

sealed class TrainingDetailsEvent {
    data object BookingSuccess : TrainingDetailsEvent()
    data class BookingError(val message: String) : TrainingDetailsEvent()
    data object WaitingListSuccess : TrainingDetailsEvent()
}
