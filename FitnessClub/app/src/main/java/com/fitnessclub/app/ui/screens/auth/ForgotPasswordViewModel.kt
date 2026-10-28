package com.fitnessclub.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ForgotPasswordStep { EMAIL, CODE }

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value.trim(), error = null)
    }

    fun onCodeChange(value: String) {
        _uiState.value = _uiState.value.copy(code = value.filter { it.isDigit() }.take(6), error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmChange(value: String) {
        _uiState.value = _uiState.value.copy(confirm = value, error = null)
    }

    fun sendCode() {
        val email = _uiState.value.email
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(error = "Укажите корректный email")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val r = authRepository.forgotPassword(email)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        step = ForgotPasswordStep.CODE,
                        info = r.data,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = r.message)
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun resetPassword() {
        val state = _uiState.value
        when {
            state.code.length != 6 -> _uiState.value = state.copy(error = "Введите 6-значный код из письма")
            state.password.length < 6 -> _uiState.value = state.copy(error = "Пароль должен быть не менее 6 символов")
            state.password != state.confirm -> _uiState.value = state.copy(error = "Пароли не совпадают")
            else -> viewModelScope.launch {
                _uiState.value = state.copy(isLoading = true, error = null)
                when (val r = authRepository.resetPassword(state.email, state.code, state.password)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            done = true,
                            info = r.data,
                        )
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = r.message)
                    }
                    is ApiResult.Loading -> Unit
                }
            }
        }
    }

    fun backToEmail() {
        _uiState.value = _uiState.value.copy(step = ForgotPasswordStep.EMAIL, error = null)
    }
}

data class ForgotPasswordUiState(
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirm: String = "",
    val step: ForgotPasswordStep = ForgotPasswordStep.EMAIL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val done: Boolean = false,
)
