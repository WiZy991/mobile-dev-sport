package com.fitnessclub.app.ui.screens.qrcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class QrCodeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val memberId: String = "",
    val qrCodeData: String? = null,
    val validUntil: String = ""
)

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(QrCodeUiState())
    val uiState: StateFlow<QrCodeUiState> = _uiState.asStateFlow()
    
    init {
        loadUserData()
    }
    
    private fun loadUserData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val user = authRepository.getCurrentUser().first()
            if (user != null) {
                val qrData = generateQrData(user.id)
                val validUntil = getValidUntilTime()
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userName = user.name,
                        memberId = user.id.takeLast(8).uppercase(),
                        qrCodeData = qrData,
                        validUntil = validUntil
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    fun refreshQrCode() {
        loadUserData()
    }
    
    private fun generateQrData(userId: String): String {
        val timestamp = System.currentTimeMillis()
        return "FITNESSCLUB:ENTRY:$userId:$timestamp"
    }
    
    private fun getValidUntilTime(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 5)
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }
}
