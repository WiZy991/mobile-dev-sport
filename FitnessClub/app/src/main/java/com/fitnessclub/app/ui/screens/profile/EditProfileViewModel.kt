package com.fitnessclub.app.ui.screens.profile

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

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val birthday: String = "",
    val avatarUrl: String? = null,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()
    
    init {
        loadProfile()
    }
    
    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val user = authRepository.getCurrentUser().first()
            if (user != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = user.name,
                        email = user.email,
                        phone = user.phone,
                        avatarUrl = user.avatarUrl
                    )
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Не удалось загрузить профиль"
                    )
                }
            }
        }
    }
    
    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }
    
    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }
    
    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phone = phone, error = null) }
    }
    
    fun updateBirthday(birthday: String) {
        _uiState.update { it.copy(birthday = birthday, error = null) }
    }
    
    fun saveProfile() {
        viewModelScope.launch {
            val state = _uiState.value
            
            // Validate
            if (state.name.isBlank()) {
                _uiState.update { it.copy(error = "Введите имя") }
                return@launch
            }
            
            if (state.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
                _uiState.update { it.copy(error = "Введите корректный email") }
                return@launch
            }
            
            if (state.phone.isBlank()) {
                _uiState.update { it.copy(error = "Введите телефон") }
                return@launch
            }
            
            _uiState.update { it.copy(isSaving = true, error = null) }
            
            // Mock save
            kotlinx.coroutines.delay(1000)
            
            _uiState.update {
                it.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            }
        }
    }
}
