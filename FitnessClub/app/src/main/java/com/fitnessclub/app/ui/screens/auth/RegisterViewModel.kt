package com.fitnessclub.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.ClubItem
import com.fitnessclub.app.data.model.RegisterRequest
import com.fitnessclub.app.data.model.User
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.ClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class RegistrationTypeOption(val label: String, val apiValue: String) {
    CLIENT("Я клиент", "client"),
    COACH("Я тренер", "coach")
}

enum class GenderOption(val label: String, val apiValue: String) {
    MALE("Муж", "male"),
    FEMALE("Жен", "female")
}

data class PassportDraft(
    val series: String = "",
    val number: String = "",
    val issuedBy: String = "",
    val issuedDateDisplay: String = "",
    val region: String = "",
    val city: String = "",
    val streetHouse: String = ""
) {
    fun isCompleteForRegister(): Boolean =
        series.isNotBlank() && number.isNotBlank() &&
            issuedBy.isNotBlank() && issuedDateDisplay.isNotBlank() &&
            region.isNotBlank() && city.isNotBlank() && streetHouse.isNotBlank()
}

data class RegisterUiState(
    val clubs: List<ClubItem> = emptyList(),
    val clubsLoading: Boolean = false,
    /** Ошибка сети/API при загрузке клубов (отличается от пустого списка) */
    val clubsLoadError: String? = null,
    val selectedClub: ClubItem? = null,
    val registrationType: RegistrationTypeOption = RegistrationTypeOption.CLIENT,
    val lastName: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val birthDateDisplay: String = "",
    val phoneNationalDigits: String = "",
    val email: String = "",
    val gender: GenderOption? = null,
    val passport: PassportDraft = PassportDraft(),
    val promoCode: String = "",
    val newsletter: Boolean = true,
    val password: String = "",
    val confirmPassword: String = "",
    val lastNameError: String? = null,
    val firstNameError: String? = null,
    val birthDateError: String? = null,
    val phoneError: String? = null,
    val emailError: String? = null,
    val genderError: String? = null,
    val passportError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val clubError: String? = null,
    /** После первого нажатия «Зарегистрироваться» показываем ошибки полей (до этого — спокойный вид) */
    val submitAttempted: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val passportSummary: String
        get() = when {
            passport.isCompleteForRegister() -> "Паспорт заполнен"
            passport.series.isNotBlank() || passport.number.isNotBlank() -> "Заполнение…"
            else -> "Заполните паспорт"
        }
}

sealed class RegisterEvent {
    data class Success(val user: User) : RegisterEvent()
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val clubRepository: ClubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    private val displayDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    init {
        loadClubs()
    }

    private fun loadClubs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                clubsLoading = true,
                clubError = null,
                clubsLoadError = null
            )
            when (val r = clubRepository.getClubs()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        clubs = r.data,
                        clubsLoading = false,
                        clubsLoadError = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        clubsLoading = false,
                        clubError = null,
                        clubsLoadError = r.message.ifBlank { "Не удалось загрузить список клубов" }
                    )
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun onClubSelected(club: ClubItem?) {
        if (club == null) {
            _uiState.value = _uiState.value.copy(selectedClub = null, clubError = null)
            return
        }
        _uiState.update { state ->
            val merged = if (state.clubs.none { it.id == club.id }) {
                listOf(club) + state.clubs
            } else {
                state.clubs
            }
            state.copy(selectedClub = club, clubs = merged, clubError = null)
        }
    }

    fun onRegistrationTypeChange(option: RegistrationTypeOption) {
        _uiState.value = _uiState.value.copy(registrationType = option)
    }

    fun onLastNameChange(v: String) {
        _uiState.value = _uiState.value.copy(lastName = v, lastNameError = null)
    }

    fun onFirstNameChange(v: String) {
        _uiState.value = _uiState.value.copy(firstName = v, firstNameError = null)
    }

    fun onMiddleNameChange(v: String) {
        _uiState.value = _uiState.value.copy(middleName = v)
    }

    fun onBirthDateChange(v: String) {
        _uiState.value = _uiState.value.copy(birthDateDisplay = v, birthDateError = null)
    }

    fun setBirthDateFromMillis(millis: Long) {
        val ld = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        _uiState.value = _uiState.value.copy(
            birthDateDisplay = ld.format(displayDateFormat),
            birthDateError = null
        )
    }

    fun onPhoneChange(raw: String) {
        val national = normalizeRussianNationalDigits(raw)
        _uiState.value = _uiState.value.copy(phoneNationalDigits = national, phoneError = null)
    }

    fun onEmailChange(v: String) {
        _uiState.value = _uiState.value.copy(email = v.trim(), emailError = null)
    }

    fun onGenderChange(g: GenderOption?) {
        _uiState.value = _uiState.value.copy(gender = g, genderError = null)
    }

    fun onPromoChange(v: String) {
        _uiState.value = _uiState.value.copy(promoCode = v)
    }

    fun onNewsletterChange(v: Boolean) {
        _uiState.value = _uiState.value.copy(newsletter = v)
    }

    fun onPasswordChange(v: String) {
        _uiState.value = _uiState.value.copy(password = v, passwordError = null)
    }

    fun onConfirmPasswordChange(v: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = v, confirmPasswordError = null)
    }

    fun onPassportSeriesChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(series = v.take(5)),
            passportError = null
        )
    }

    fun onPassportNumberChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(number = v.take(7)),
            passportError = null
        )
    }

    fun onPassportIssuedByChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(issuedBy = v.take(300))
        )
    }

    fun onPassportIssuedDateChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(issuedDateDisplay = v)
        )
    }

    fun setPassportIssuedDateFromMillis(millis: Long) {
        val ld = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(
                issuedDateDisplay = ld.format(displayDateFormat)
            )
        )
    }

    fun onPassportRegionChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(region = v)
        )
    }

    fun onPassportCityChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(city = v)
        )
    }

    fun onPassportStreetChange(v: String) {
        _uiState.value = _uiState.value.copy(
            passport = _uiState.value.passport.copy(streetHouse = v)
        )
    }

    /** Валидация экрана паспорта перед возвратом. */
    fun validatePassportDraft(): Boolean {
        val p = _uiState.value.passport
        var ok = true
        if (p.series.isBlank()) ok = false
        if (p.number.isBlank()) ok = false
        if (p.issuedBy.isBlank()) ok = false
        if (parseToIsoDate(p.issuedDateDisplay) == null) ok = false
        if (p.region.isBlank() || p.city.isBlank() || p.streetHouse.isBlank()) ok = false
        if (!ok) {
            _uiState.value = _uiState.value.copy(passportError = "Заполните все поля паспорта корректно")
        } else {
            _uiState.value = _uiState.value.copy(passportError = null)
        }
        return ok
    }

    fun clearPassportError() {
        _uiState.value = _uiState.value.copy(passportError = null)
    }

    fun register() {
        _uiState.value = _uiState.value.copy(submitAttempted = true)
        val state = _uiState.value
        var lastNameError: String? = null
        var firstNameError: String? = null
        var birthDateError: String? = null
        var phoneError: String? = null
        var emailError: String? = null
        var genderError: String? = null
        var passportError: String? = null
        var passwordError: String? = null
        var confirmPasswordError: String? = null
        var clubError: String? = null
        var hasError = false

        if (state.selectedClub == null) {
            clubError = "Выберите клуб"
            hasError = true
        }
        if (state.lastName.isBlank()) {
            lastNameError = "Введите фамилию"
            hasError = true
        }
        if (state.firstName.isBlank()) {
            firstNameError = "Введите имя"
            hasError = true
        }
        val birthIso = parseToIsoDate(state.birthDateDisplay)
        if (birthIso == null) {
            birthDateError = "Укажите дату рождения (дд.мм.гггг)"
            hasError = true
        }
        val phoneApi = phoneForApi(state.phoneNationalDigits)
        if (phoneApi.isEmpty()) {
            phoneError = "Введите полный номер телефона"
            hasError = true
        }
        if (state.email.isBlank()) {
            emailError = "Введите email"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            emailError = "Неверный формат email"
            hasError = true
        }
        if (state.gender == null) {
            genderError = "Выберите пол"
            hasError = true
        }
        if (!state.passport.isCompleteForRegister()) {
            passportError = "Заполните паспорт"
            hasError = true
        }
        val passportIssueIso = parseToIsoDate(state.passport.issuedDateDisplay)
        if (state.passport.isCompleteForRegister() && passportIssueIso == null) {
            passportError = "Неверная дата выдачи паспорта"
            hasError = true
        }
        if (state.password.isBlank()) {
            passwordError = "Введите пароль"
            hasError = true
        } else if (state.password.length < 6) {
            passwordError = "Пароль не менее 6 символов"
            hasError = true
        }
        if (state.confirmPassword != state.password) {
            confirmPasswordError = "Пароли не совпадают"
            hasError = true
        }

        if (hasError) {
            _uiState.value = state.copy(
                lastNameError = lastNameError,
                firstNameError = firstNameError,
                birthDateError = birthDateError,
                phoneError = phoneError,
                emailError = emailError,
                genderError = genderError,
                passportError = passportError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError,
                clubError = clubError
            )
            return
        }

        val fullName = listOf(state.lastName, state.firstName, state.middleName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val address = listOf(
            state.passport.region,
            state.passport.city,
            state.passport.streetHouse
        ).filter { it.isNotBlank() }
            .joinToString(", ")

        val request = RegisterRequest(
            email = state.email,
            password = state.password,
            phone = phoneApi,
            name = fullName,
            registrationType = state.registrationType.apiValue,
            dateOfBirth = birthIso,
            gender = state.gender!!.apiValue,
            passportSeries = state.passport.series,
            passportNumber = state.passport.number,
            passportIssuedBy = state.passport.issuedBy,
            passportIssueDate = passportIssueIso,
            registrationAddress = address,
            promoCode = state.promoCode.takeIf { it.isNotBlank() },
            newsletter = state.newsletter,
            clubId = state.selectedClub?.id
        )

        viewModelScope.launch {
            authRepository.register(request).collect { result ->
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

    private fun parseToIsoDate(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t, displayDateFormat).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            try {
                LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }
    }
}
