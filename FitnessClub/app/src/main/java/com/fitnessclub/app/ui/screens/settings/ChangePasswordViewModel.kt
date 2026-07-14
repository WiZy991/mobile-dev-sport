package com.fitnessclub.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSaving: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun updateCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value, error = null) }
    }

    fun updateNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value, error = null) }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.newPassword.length < 6) {
            _uiState.update { it.copy(error = "Пароль должен быть не менее 6 символов") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(error = "Пароли не совпадают") }
            return
        }

        viewModelScope.launch {
            authRepository.changePassword(
                currentPassword = state.currentPassword,
                newPassword = state.newPassword,
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _uiState.update { it.copy(isSaving = true, error = null) }
                    is ApiResult.Success -> _uiState.update {
                        it.copy(isSaving = false, success = true, error = null)
                    }
                    is ApiResult.Error -> _uiState.update {
                        it.copy(isSaving = false, error = result.message)
                    }
                }
            }
        }
    }
}
