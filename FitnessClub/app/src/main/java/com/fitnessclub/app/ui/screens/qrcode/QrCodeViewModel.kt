package com.fitnessclub.app.ui.screens.qrcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.repository.AccessRepository
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
    val isInsideGym: Boolean = false,
    val secondsRemaining: Int = 15,
)

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accessRepository: AccessRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrCodeUiState())
    val uiState: StateFlow<QrCodeUiState> = _uiState.asStateFlow()

    private var rotationJob: Job? = null
    private var statusJob: Job? = null

    init {
        viewModelScope.launch {
            accessRepository.refreshAccessStatus()
            _uiState.update { it.copy(isInsideGym = accessRepository.accessStatus.value.isInside) }
            startQrRotation()
            startAccessStatusPolling()
        }
    }

    fun refreshQrCode() {
        viewModelScope.launch {
            accessRepository.refreshAccessStatus()
            rotationJob?.cancel()
            startQrRotation()
        }
    }

    private fun startAccessStatusPolling() {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            while (isActive) {
                accessRepository.refreshAccessStatus()
                val isInside = accessRepository.accessStatus.value.isInside
                val wasInside = _uiState.value.isInsideGym
                if (isInside != wasInside) {
                    _uiState.update { it.copy(isInsideGym = isInside) }
                    rotationJob?.cancel()
                    startQrRotation()
                } else {
                    _uiState.update { it.copy(isInsideGym = isInside) }
                }
                delay(5_000)
            }
        }
    }

    private fun startQrRotation() {
        rotationJob = viewModelScope.launch {
            val user = authRepository.getCurrentUser().first()
            if (user == null) {
                _uiState.update {
                    it.copy(isLoading = false, qrCodeData = null, secondsRemaining = 0)
                }
                return@launch
            }

            val isInside = accessRepository.accessStatus.value.isInside
            _uiState.update { it.copy(isInsideGym = isInside) }

            if (isInside) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userName = user.name,
                        memberId = user.id.takeLast(8).uppercase(),
                        qrCodeData = generateQrData(user.id, System.currentTimeMillis()),
                        secondsRemaining = 0,
                    )
                }
                return@launch
            }

            while (isActive) {
                val ts = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userName = user.name,
                        memberId = user.id.takeLast(8).uppercase(),
                        qrCodeData = generateQrData(user.id, ts),
                        secondsRemaining = 15,
                    )
                }
                repeat(15) {
                    delay(1_000)
                    if (!isActive) return@launch
                    _uiState.update { state -> state.copy(secondsRemaining = maxOf(0, state.secondsRemaining - 1)) }
                }
            }
        }
    }

    override fun onCleared() {
        rotationJob?.cancel()
        statusJob?.cancel()
        super.onCleared()
    }

    private fun generateQrData(userId: String, timestamp: Long): String {
        val uid = if (userId.lowercase().startsWith("user-")) {
            userId.substring(5)
        } else {
            userId
        }
        val t = encodeTimestampBase62(timestamp)
        return "FITNESSCLUB:ENTRY:$uid:$t"
    }

    private fun encodeTimestampBase62(ms: Long): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        var v = ms.coerceAtLeast(0L)
        val sb = StringBuilder(7)
        repeat(7) {
            val idx = (v % 62L).toInt()
            sb.insert(0, alphabet[idx])
            v /= 62L
        }
        return sb.toString()
    }
}
