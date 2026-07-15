package com.fitnessclub.app.ui.screens.mytrainings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.Booking
import com.fitnessclub.app.data.model.BookingStatus
import com.fitnessclub.app.data.model.isUpcoming
import com.fitnessclub.app.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyTrainingsViewModel @Inject constructor(
    private val trainingRepository: TrainingRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MyTrainingsUiState())
    val uiState: StateFlow<MyTrainingsUiState> = _uiState.asStateFlow()
    
    init {
        loadBookings()
    }
    
    fun loadBookings() {
        viewModelScope.launch {
            trainingRepository.getMyBookings().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        val bookings = result.data
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            upcomingBookings = bookings.filter { it.status.isUpcoming() },
                            pastBookings = bookings.filter { 
                                it.status == BookingStatus.COMPLETED || it.status == BookingStatus.CANCELLED 
                            },
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
    
    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            trainingRepository.cancelBooking(bookingId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        loadBookings() // Reload after cancellation
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(error = result.message)
                    }
                    is ApiResult.Loading -> {}
                }
            }
        }
    }
    
    fun refresh() {
        loadBookings()
    }
}

data class MyTrainingsUiState(
    val upcomingBookings: List<Booking> = emptyList(),
    val pastBookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
