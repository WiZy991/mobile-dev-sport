package com.fitnessclub.app.ui.screens.qrcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QrCodeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val memberId: String = "",
    val qrCodeData: String? = null,
    /** Текст для совместимости; основной таймер — secondsRemaining */
    val validUntil: String = "",
    val secondsRemaining: Int = 15
)

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrCodeUiState())
    val uiState: StateFlow<QrCodeUiState> = _uiState.asStateFlow()

    private var rotationJob: Job? = null

    init {
        startQrRotation()
    }

    fun refreshQrCode() {
        rotationJob?.cancel()
        startQrRotation()
    }

    private fun startQrRotation() {
        rotationJob = viewModelScope.launch {
            while (isActive) {
                val user = authRepository.getCurrentUser().first()
                if (user == null) {
                    _uiState.update {
                        it.copy(isLoading = false, qrCodeData = null, secondsRemaining = 0)
                    }
                    return@launch
                }
                val ts = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userName = user.name,
                        memberId = user.id.takeLast(8).uppercase(),
                        qrCodeData = generateQrData(user.id, ts),
                        secondsRemaining = 15,
                        validUntil = ""
                    )
                }
                repeat(15) {
                    delay(1_000)
                    if (!isActive) return@launch
                    _uiState.update { s -> s.copy(secondsRemaining = maxOf(0, s.secondsRemaining - 1)) }
                }
            }
        }
    }

    override fun onCleared() {
        rotationJob?.cancel()
        super.onCleared()
    }

    private fun generateQrData(userId: String, timestamp: Long): String {
        return "FITNESSCLUB:ENTRY:$userId:$timestamp"
    }
}
