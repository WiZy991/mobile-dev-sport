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

enum class RegisterFormStep(val title: String, val index: Int) {
    PERSONAL("Личные данные", 1),
    PASSPORT("Паспорт", 2),
    ACCOUNT("Пароль и согласие", 3),
    ;

    companion object {
        val LAST = ACCOUNT
    }
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
    val acceptedLegalTerms: Boolean = false,
    val password: String = "",
    val confirmPassword: String = "",
    /** Опросник «Откуда узнали о нас»: выбранный ключ варианта (см. ReferralSourceOptions). */
    val referralSource: String? = null,
    /** Свой вариант, если выбрано «Другое». */
    val referralSourceOther: String = "",
    val referralError: String? = null,
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
    val legalTermsError: String? = null,
    /** Показывать ошибки полей после попытки «Далее» / «Зарегистрироваться». */
    val showValidationErrors: Boolean = false,
    val formStep: RegisterFormStep = RegisterFormStep.PERSONAL,
    /** Краткое сообщение над кнопкой, если форма не прошла проверку. */
    val validationSummary: String? = null,
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

    fun fieldError(message: String?): String? =
        if (showValidationErrors || submitAttempted) message else null

    val firstBlockingError: String?
        get() = listOfNotNull(
            clubError,
            lastNameError,
            firstNameError,
            birthDateError,
            phoneError,
            emailError,
            genderError,
            passportError,
            passwordError,
            confirmPasswordError,
            legalTermsError,
        ).firstOrNull()
}

sealed class RegisterEvent {
    data class Success(val user: User) : RegisterEvent()
}

/** Варианты опросника «Откуда вы о нас узнали»: стабильный ключ + подпись. */
object ReferralSourceOptions {
    const val OTHER_KEY = "other"

    val items: List<Pair<String, String>> = listOf(
        "friends_family" to "От друзей/родственников",
        "friends_in_gymroom" to "Друзья/родственники уже ходят в Доброзал",
        "social" to "Из социальных сетей",
        "2gis" to "2ГИС",
        "yandex" to "Яндекс",
        "internet_ads" to "Из рекламы в интернете",
        "mall" to "Увидел в торговом центре",
        "event" to "Посещал мероприятие",
        OTHER_KEY to "Другое (свой вариант)",
    )
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

    /** Обновление полей формы: сбрасывает краткое сообщение над кнопкой при исправлении. */
    private inline fun updateForm(crossinline block: (RegisterUiState) -> RegisterUiState) {
        _uiState.update { block(it).copy(validationSummary = null) }
    }

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
            updateForm { it.copy(selectedClub = null, clubError = null) }
            return
        }
        updateForm { state ->
            val merged = if (state.clubs.none { it.id == club.id }) {
                listOf(club) + state.clubs
            } else {
                state.clubs
            }
            state.copy(selectedClub = club, clubs = merged, clubError = null)
        }
    }

    fun onRegistrationTypeChange(option: RegistrationTypeOption) {
        updateForm { it.copy(registrationType = option) }
    }

    fun onLastNameChange(v: String) {
        updateForm { it.copy(lastName = v, lastNameError = null) }
    }

    fun onFirstNameChange(v: String) {
        updateForm { it.copy(firstName = v, firstNameError = null) }
    }

    fun onMiddleNameChange(v: String) {
        updateForm { it.copy(middleName = v) }
    }

    fun onBirthDateChange(v: String) {
        updateForm { it.copy(birthDateDisplay = v, birthDateError = null) }
    }

    fun setBirthDateFromMillis(millis: Long) {
        val ld = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        updateForm {
            it.copy(
                birthDateDisplay = ld.format(displayDateFormat),
                birthDateError = null,
            )
        }
    }

    fun onPhoneChange(raw: String) {
        val national = normalizeRussianNationalDigits(raw)
        updateForm { it.copy(phoneNationalDigits = national, phoneError = null) }
    }

    fun onEmailChange(v: String) {
        updateForm { it.copy(email = v.trim(), emailError = null) }
    }

    fun onGenderChange(g: GenderOption?) {
        updateForm { it.copy(gender = g, genderError = null) }
    }

    fun onPromoChange(v: String) {
        updateForm { it.copy(promoCode = v) }
    }

    fun onNewsletterChange(v: Boolean) {
        updateForm { it.copy(newsletter = v) }
    }

    fun onAcceptedLegalTermsChange(v: Boolean) {
        updateForm { it.copy(acceptedLegalTerms = v, legalTermsError = null) }
    }

    fun onPasswordChange(v: String) {
        updateForm { it.copy(password = v, passwordError = null) }
    }

    fun onConfirmPasswordChange(v: String) {
        updateForm { it.copy(confirmPassword = v, confirmPasswordError = null) }
    }

    fun onReferralSourceSelected(key: String) {
        updateForm { it.copy(referralSource = key, referralError = null) }
    }

    fun onReferralSourceOtherChange(v: String) {
        updateForm { it.copy(referralSourceOther = v.take(255), referralError = null) }
    }

    fun onPassportSeriesChange(v: String) {
        updateForm {
            it.copy(
                passport = it.passport.copy(series = v.take(5)),
                passportError = null,
            )
        }
    }

    fun onPassportNumberChange(v: String) {
        updateForm {
            it.copy(
                passport = it.passport.copy(number = v.take(7)),
                passportError = null,
            )
        }
    }

    fun onPassportIssuedByChange(v: String) {
        updateForm {
            it.copy(passport = it.passport.copy(issuedBy = v.take(300)), passportError = null)
        }
    }

    fun onPassportIssuedDateChange(v: String) {
        updateForm {
            it.copy(passport = it.passport.copy(issuedDateDisplay = v), passportError = null)
        }
    }

    fun setPassportIssuedDateFromMillis(millis: Long) {
        val ld = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        updateForm {
            it.copy(
                passport = it.passport.copy(issuedDateDisplay = ld.format(displayDateFormat)),
                passportError = null,
            )
        }
    }

    fun onPassportRegionChange(v: String) {
        updateForm {
            it.copy(passport = it.passport.copy(region = v), passportError = null)
        }
    }

    fun onPassportCityChange(v: String) {
        updateForm {
            it.copy(passport = it.passport.copy(city = v), passportError = null)
        }
    }

    fun onPassportStreetChange(v: String) {
        updateForm {
            it.copy(passport = it.passport.copy(streetHouse = v), passportError = null)
        }
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

    fun goToPreviousFormStep() {
        val prev = when (_uiState.value.formStep) {
            RegisterFormStep.PERSONAL -> return
            RegisterFormStep.PASSPORT -> RegisterFormStep.PERSONAL
            RegisterFormStep.ACCOUNT -> RegisterFormStep.PASSPORT
        }
        _uiState.update {
            it.copy(
                formStep = prev,
                validationSummary = null,
                showValidationErrors = false,
            )
        }
    }

    /** Следующий шаг формы. При ошибке — подсветка полей и текст над кнопкой. */
    fun advanceFormStep(): Boolean {
        val step = _uiState.value.formStep
        val errors = validateStep(step)
        if (errors.hasError) {
            applyValidationErrors(errors, summaryForStep(step, errors), step)
            return false
        }
        if (step == RegisterFormStep.LAST) return true
        val next = when (step) {
            RegisterFormStep.PERSONAL -> RegisterFormStep.PASSPORT
            RegisterFormStep.PASSPORT -> RegisterFormStep.ACCOUNT
            RegisterFormStep.ACCOUNT -> RegisterFormStep.ACCOUNT
        }
        _uiState.update {
            it.copy(
                formStep = next,
                showValidationErrors = false,
                validationSummary = null,
            )
        }
        return true
    }

    fun onPrimaryFormAction() {
        val step = _uiState.value.formStep
        if (step == RegisterFormStep.ACCOUNT) {
            register()
        } else {
            advanceFormStep()
        }
    }
    
    /**
     * Опросник «Откуда вы о нас узнали» — первый шаг регистрации.
     * Проверяет выбор перед переходом к выбору клуба. В CRM ничего не отправляет:
     * выбор сохраняется и уходит вместе с регистрацией (см. register()).
     */
    fun submitSurvey(): Boolean {
        val state = _uiState.value
        if (state.referralSource.isNullOrBlank()) {
            _uiState.value = state.copy(referralError = "Выберите вариант")
            return false
        }
        if (state.referralSource == ReferralSourceOptions.OTHER_KEY && state.referralSourceOther.isBlank()) {
            _uiState.value = state.copy(referralError = "Опишите ваш вариант")
            return false
        }
        return true
    }

    /** Завершение регистрации: данные опросника уходят в CRM вместе с остальными полями. */
    fun register() {
        val request = validateAndBuild() ?: return
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

    /** Валидация полей формы. Возвращает готовый запрос (с данными опросника) или null при ошибке. */
    private fun validateAndBuild(): RegisterRequest? {
        val personal = validateStep(RegisterFormStep.PERSONAL)
        val passport = validateStep(RegisterFormStep.PASSPORT)
        val account = validateStep(RegisterFormStep.ACCOUNT)
        val merged = personal.merge(passport).merge(account)
        if (merged.hasError) {
            val targetStep = when {
                personal.hasError -> RegisterFormStep.PERSONAL
                passport.hasError -> RegisterFormStep.PASSPORT
                else -> RegisterFormStep.ACCOUNT
            }
            applyValidationErrors(
                merged,
                merged.firstMessage() ?: "Заполните обязательные поля",
                targetStep,
            )
            return null
        }

        val state = _uiState.value
        val birthIso = parseToIsoDate(state.birthDateDisplay)!!
        val phoneApi = phoneForApi(state.phoneNationalDigits)
        val passportIssueIso = parseToIsoDate(state.passport.issuedDateDisplay)!!

        val fullName = listOf(state.lastName, state.firstName, state.middleName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val address = listOf(
            state.passport.region,
            state.passport.city,
            state.passport.streetHouse
        ).filter { it.isNotBlank() }
            .joinToString(", ")

        return RegisterRequest(
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
            clubId = state.selectedClub?.id,
            referralSource = state.referralSource,
            referralSourceOther = state.referralSourceOther.takeIf { it.isNotBlank() }
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private data class FieldErrors(
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
        val legalTermsError: String? = null,
    ) {
        val hasError: Boolean
            get() = listOfNotNull(
                lastNameError,
                firstNameError,
                birthDateError,
                phoneError,
                emailError,
                genderError,
                passportError,
                passwordError,
                confirmPasswordError,
                clubError,
                legalTermsError,
            ).isNotEmpty()

        fun firstMessage(): String? = listOfNotNull(
            clubError,
            lastNameError,
            firstNameError,
            birthDateError,
            phoneError,
            emailError,
            genderError,
            passportError,
            passwordError,
            confirmPasswordError,
            legalTermsError,
        ).firstOrNull()

        fun merge(other: FieldErrors): FieldErrors = copy(
            lastNameError = lastNameError ?: other.lastNameError,
            firstNameError = firstNameError ?: other.firstNameError,
            birthDateError = birthDateError ?: other.birthDateError,
            phoneError = phoneError ?: other.phoneError,
            emailError = emailError ?: other.emailError,
            genderError = genderError ?: other.genderError,
            passportError = passportError ?: other.passportError,
            passwordError = passwordError ?: other.passwordError,
            confirmPasswordError = confirmPasswordError ?: other.confirmPasswordError,
            clubError = clubError ?: other.clubError,
            legalTermsError = legalTermsError ?: other.legalTermsError,
        )
    }

    private fun validateStep(step: RegisterFormStep): FieldErrors {
        val state = _uiState.value
        var errors = FieldErrors()
        when (step) {
            RegisterFormStep.PERSONAL -> {
                if (state.selectedClub == null) {
                    errors = errors.copy(clubError = "Выберите клуб на предыдущем шаге")
                }
                if (state.lastName.isBlank()) {
                    errors = errors.copy(lastNameError = "Введите фамилию")
                }
                if (state.firstName.isBlank()) {
                    errors = errors.copy(firstNameError = "Введите имя")
                }
                if (parseToIsoDate(state.birthDateDisplay) == null) {
                    errors = errors.copy(birthDateError = "Укажите дату рождения (дд.мм.гггг)")
                }
                if (phoneForApi(state.phoneNationalDigits).isEmpty()) {
                    errors = errors.copy(phoneError = "Введите полный номер телефона")
                }
                if (state.email.isBlank()) {
                    errors = errors.copy(emailError = "Введите email")
                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
                    errors = errors.copy(emailError = "Неверный формат email")
                }
                if (state.gender == null) {
                    errors = errors.copy(genderError = "Выберите пол")
                }
            }
            RegisterFormStep.PASSPORT -> {
                if (!state.passport.isCompleteForRegister()) {
                    errors = errors.copy(passportError = "Заполните все поля паспорта")
                } else if (parseToIsoDate(state.passport.issuedDateDisplay) == null) {
                    errors = errors.copy(passportError = "Неверная дата выдачи паспорта")
                }
            }
            RegisterFormStep.ACCOUNT -> {
                if (state.password.isBlank()) {
                    errors = errors.copy(passwordError = "Введите пароль")
                } else if (state.password.length < 6) {
                    errors = errors.copy(passwordError = "Пароль не менее 6 символов")
                }
                if (state.confirmPassword != state.password) {
                    errors = errors.copy(confirmPasswordError = "Пароли не совпадают")
                }
                if (!state.acceptedLegalTerms) {
                    errors = errors.copy(legalTermsError = "Подтвердите согласие с условиями")
                }
            }
        }
        return errors
    }

    private fun applyValidationErrors(
        errors: FieldErrors,
        summary: String,
        targetStep: RegisterFormStep? = null,
    ) {
        val step = targetStep ?: _uiState.value.formStep
        _uiState.update { state ->
            state.copy(
                formStep = step,
                showValidationErrors = true,
                submitAttempted = step == RegisterFormStep.ACCOUNT,
                validationSummary = summary,
                lastNameError = errors.lastNameError,
                firstNameError = errors.firstNameError,
                birthDateError = errors.birthDateError,
                phoneError = errors.phoneError,
                emailError = errors.emailError,
                genderError = errors.genderError,
                passportError = errors.passportError,
                passwordError = errors.passwordError,
                confirmPasswordError = errors.confirmPasswordError,
                clubError = errors.clubError,
                legalTermsError = errors.legalTermsError,
            )
        }
    }

    private fun summaryForStep(step: RegisterFormStep, errors: FieldErrors): String {
        val detail = errors.firstMessage()
        return when (step) {
            RegisterFormStep.PERSONAL -> detail ?: "Заполните личные данные"
            RegisterFormStep.PASSPORT -> detail ?: "Заполните паспортные данные"
            RegisterFormStep.ACCOUNT -> detail ?: "Заполните пароль и подтвердите согласие"
        }
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
