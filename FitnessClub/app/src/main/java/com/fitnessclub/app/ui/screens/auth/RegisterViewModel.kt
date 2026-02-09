package com.fitnessclub.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.model.User
import com.fitnessclub.app.data.repository.AuthRepository
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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()
    
    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email, emailError = null)
    }
    
    fun onPhoneChange(phone: String) {
        _uiState.value = _uiState.value.copy(phone = phone, phoneError = null)
    }
    
    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, passwordError = null)
    }
    
    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = confirmPassword,
            confirmPasswordError = null
        )
    }
    
    fun register() {
        val state = _uiState.value
        
        // Validation
        var hasError = false
        var nameError: String? = null
        var emailError: String? = null
        var phoneError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null
        
        if (state.name.isBlank()) {
            nameError = "Введите имя"
            hasError = true
        }
        
        if (state.email.isBlank()) {
            emailError = "Введите email"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            emailError = "Неверный формат email"
            hasError = true
        }
        
        if (state.phone.isBlank()) {
            phoneError = "Введите телефон"
            hasError = true
        } else if (!state.phone.matches(Regex("^\\+?[0-9]{10,15}$"))) {
            phoneError = "Неверный формат телефона"
            hasError = true
        }
        
        if (state.password.isBlank()) {
            passwordError = "Введите пароль"
            hasError = true
        } else if (state.password.length < 6) {
            passwordError = "Пароль должен быть не менее 6 символов"
            hasError = true
        }
        
        if (state.confirmPassword != state.password) {
            confirmPasswordError = "Пароли не совпадают"
            hasError = true
        }
        
        if (hasError) {
            _uiState.value = state.copy(
                nameError = nameError,
                emailError = emailError,
                phoneError = phoneError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError
            )
            return
        }
        
        viewModelScope.launch {
            authRepository.register(
                name = state.name,
                email = state.email,
                phone = state.phone,
                password = state.password
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(RegisterEvent.Success(result.data))
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
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class RegisterEvent {
    data class Success(val user: User) : RegisterEvent()
}
