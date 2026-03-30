package com.fitnessclub.app.ui.screens.lockers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.Locker
import com.fitnessclub.app.data.api.LockerBooking
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockerUiState(
    val isLoading: Boolean = true,
    val lockers: List<Locker> = emptyList(),
    val myBooking: LockerBooking? = null,
    val error: String? = null
)

@HiltViewModel
class LockerViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockerUiState())
    val uiState: StateFlow<LockerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val lockersResponse = api.getLockers()
                val bookingResponse = api.getMyLockerBooking()
                val lockers = lockersResponse.body() ?: emptyList()
                val booking = bookingResponse.body()
                if (booking != null && !isBookingActive(booking)) {
                    // Ignore expired booking
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lockers = lockers,
                            myBooking = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lockers = lockers,
                            myBooking = booking
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    fun bookLocker(lockerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val response = api.bookLocker(lockerId)
                if (response.isSuccessful) {
                    response.body()?.let { load() }
                } else {
                    _uiState.update {
                        it.copy(error = "Не удалось забронировать. Шкафчик занят?")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Ошибка бронирования")
                }
            }
        }
    }

    fun releaseLocker() {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                api.releaseLocker()
                load()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Ошибка освобождения")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun isBookingActive(booking: LockerBooking): Boolean {
        return try {
            val endsAt = java.time.Instant.parse(booking.endsAt.replace(" ", "T"))
            java.time.Instant.now().isBefore(endsAt)
        } catch (_: Exception) {
            false
        }
    }
}
