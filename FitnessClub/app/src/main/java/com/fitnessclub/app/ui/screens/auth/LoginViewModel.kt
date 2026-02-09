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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()
    
    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = null
        )
    }
    
    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null
        )
    }
    
    fun login() {
        val state = _uiState.value
        
        // Validation
        var hasError = false
        var emailError: String? = null
        var passwordError: String? = null
        
        if (state.email.isBlank()) {
            emailError = "Введите email"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            emailError = "Неверный формат email"
            hasError = true
        }
        
        if (state.password.isBlank()) {
            passwordError = "Введите пароль"
            hasError = true
        } else if (state.password.length < 6) {
            passwordError = "Пароль должен быть не менее 6 символов"
            hasError = true
        }
        
        if (hasError) {
            _uiState.value = state.copy(
                emailError = emailError,
                passwordError = passwordError
            )
            return
        }
        
        viewModelScope.launch {
            authRepository.login(state.email, state.password).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(LoginEvent.Success(result.data))
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class LoginEvent {
    data class Success(val user: User) : LoginEvent()
}
