package com.fitnessclub.app.ui.screens.referral

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
import javax.inject.Inject

data class ReferralUiState(
    val isLoading: Boolean = true,
    val referralCode: String = "",
    val referralLink: String = "",
    val invitedCount: Int = 0,
    val registeredCount: Int = 0,
    val earnedBonuses: Int = 0
)

@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ReferralUiState())
    val uiState: StateFlow<ReferralUiState> = _uiState.asStateFlow()
    
    init {
        loadReferralData()
    }
    
    private fun loadReferralData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val user = authRepository.getCurrentUser().first()
            if (user != null) {
                // Generate referral code from user ID
                val code = "REF${user.id.takeLast(6).uppercase()}"
                val link = "https://fitnessclub.app/invite/$code"
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        referralCode = code,
                        referralLink = link,
                        // Mock statistics
                        invitedCount = 5,
                        registeredCount = 3,
                        earnedBonuses = 1500
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
