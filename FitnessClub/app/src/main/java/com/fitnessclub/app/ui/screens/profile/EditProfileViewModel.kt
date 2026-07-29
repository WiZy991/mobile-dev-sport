package com.fitnessclub.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.ui.screens.auth.normalizeRussianNationalDigits
import com.fitnessclub.app.ui.screens.auth.phoneForApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val name: String = "",
    val email: String = "",
    /** 10 национальных цифр (без +7). */
    val phoneNationalDigits: String = "",
    /** Только цифры даты рождения, до 8 (ддммгггг). */
    val birthdayDigits: String = "",
    val avatarUrl: String? = null,
    val error: String? = null
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
                        phoneNationalDigits = normalizeRussianNationalDigits(user.phone),
                        birthdayDigits = isoToBirthdayDigits(user.dateOfBirth),
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

    fun updatePhone(raw: String) {
        _uiState.update {
            it.copy(
                phoneNationalDigits = normalizeRussianNationalDigits(raw),
                error = null,
            )
        }
    }

    fun updateBirthday(raw: String) {
        _uiState.update {
            it.copy(
                birthdayDigits = raw.filter { c -> c.isDigit() }.take(8),
                error = null,
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val state = _uiState.value

            if (state.name.isBlank()) {
                _uiState.update { it.copy(error = "Введите имя") }
                return@launch
            }

            if (state.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
                _uiState.update { it.copy(error = "Введите корректный email") }
                return@launch
            }

            val phoneApi = phoneForApi(state.phoneNationalDigits)
            if (phoneApi.isEmpty()) {
                _uiState.update { it.copy(error = "Введите полный номер телефона") }
                return@launch
            }

            val birthdayIso = when {
                state.birthdayDigits.isEmpty() -> null
                state.birthdayDigits.length != 8 -> {
                    _uiState.update { it.copy(error = "Дата рождения: формат ДД.ММ.ГГГГ") }
                    return@launch
                }
                else -> parseDigitsToIso(state.birthdayDigits).also { parsed ->
                    if (parsed == null) {
                        _uiState.update { it.copy(error = "Некорректная дата рождения") }
                        return@launch
                    }
                }
            }

            _uiState.update { it.copy(isSaving = true, error = null) }

            when (val result = authRepository.updateProfile(
                name = state.name,
                email = state.email,
                phone = phoneApi,
                dateOfBirth = birthdayIso,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = true,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.message,
                        )
                    }
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun isoToBirthdayDigits(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val ld = LocalDate.parse(iso.trim().take(10))
            ld.format(displayDateFormat).filter { it.isDigit() }
        } catch (_: Exception) {
            iso.filter { it.isDigit() }.take(8)
        }
    }

    private fun parseDigitsToIso(digits: String): String? {
        if (digits.length != 8) return null
        return try {
            val display = DateDotsVisualTransformation.formatDateDots(digits)
            LocalDate.parse(display, displayDateFormat).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }
}
