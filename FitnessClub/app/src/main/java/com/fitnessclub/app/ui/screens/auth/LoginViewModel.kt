package com.fitnessclub.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.auth.SberPkce
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.local.AuthFlowStore
import com.fitnessclub.app.data.local.BiometricLoginCoordinator
import com.fitnessclub.app.data.local.BiometricLoginStore
import com.fitnessclub.app.data.model.LoginHintResult
import com.fitnessclub.app.data.model.User
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.data.repository.isSessionRejected
import com.fitnessclub.app.data.repository.loginPasswordRequiredMessage
import com.fitnessclub.app.data.repository.passwordNotSetHintMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val clubRepository: ClubRepository,
    private val authFlowStore: AuthFlowStore,
    private val biometricLoginStore: BiometricLoginStore,
) : ViewModel() {
    private val sberRedirectUri = BuildConfig.SBER_REDIRECT_URI
    private var sberCodeVerifier: String? = null
    private var welcomeAfterSberLogin = false

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        refreshBiometricOffer()
        loadClubBrandName()
        loadOtpChannels()
        viewModelScope.launch {
            authFlowStore.hasCompletedRegistration.collect { completed ->
                _uiState.value = _uiState.value.copy(hasCompletedRegistration = completed)
            }
        }
    }

    fun setWelcomeAfterSberLogin(enabled: Boolean) {
        welcomeAfterSberLogin = enabled
    }

    private fun loadClubBrandName() {
        viewModelScope.launch {
            when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    val brand = Brand.orFallback(result.data.brandName)
                    _uiState.value = _uiState.value.copy(
                        clubBrandName = brand,
                        supportEmail = result.data.email.trim().takeIf { it.isNotEmpty() },
                        supportPhone = result.data.phone.trim().takeIf { it.isNotEmpty() },
                    )
                }
                else -> Unit
            }
        }
    }

    private fun loadOtpChannels() {
        viewModelScope.launch {
            when (val result = authRepository.otpChannels()) {
                is ApiResult.Success -> {
                    val available = result.data.associate { it.id to it.available }
                    val current = _uiState.value.otpChannel
                    val selected = if (available[current] == true) {
                        current
                    } else {
                        listOf("telegram", "max", "whatsapp").firstOrNull { available[it] == true } ?: current
                    }
                    _uiState.value = _uiState.value.copy(
                        otpChannelAvailable = available,
                        otpChannelsLoaded = true,
                        otpChannel = selected,
                    )
                }
                else -> _uiState.value = _uiState.value.copy(otpChannelsLoaded = true)
            }
        }
    }

    fun refreshBiometricOffer() {
        _uiState.value = _uiState.value.copy(
            biometricLoginConfigured = biometricLoginStore.shouldShowBiometricLoginButton(),
            biometricHardwareReady = biometricLoginStore.canUseDeviceBiometric(),
        )
    }

    /** Кнопка на экране входа всегда видна: подсказки, если ещё не настроена биометрия в приложении. */
    fun onBiometricLoginClick(activity: FragmentActivity) {
        if (!biometricLoginStore.hasStoredCredential()) {
            _uiState.value = _uiState.value.copy(
                error = "Сначала войдите по email и паролю. Затем в Настройки → Безопасность включите «Биометрию».",
            )
            return
        }
        if (!biometricLoginStore.canUseDeviceBiometric()) {
            _uiState.value = _uiState.value.copy(
                error = "Добавьте отпечаток в настройках телефона (раздел «Безопасность» / «Отпечаток пальца»).",
            )
            return
        }
        loginWithBiometric(activity)
    }

    fun loginWithBiometric(activity: FragmentActivity) {
        if (!biometricLoginStore.hasStoredCredential()) return
        BiometricLoginCoordinator.startDecryptPrompt(activity, biometricLoginStore) { rt, err, broken ->
            if (rt == null) {
                if (broken) {
                    biometricLoginStore.clear()
                }
                refreshBiometricOffer()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = err?.takeIf { it.isNotBlank() },
                )
                return@startDecryptPrompt
            }
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                when (val result = authRepository.restoreSessionForBiometric(rt)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        refreshBiometricOffer()
                        _events.emit(LoginEvent.Success(result.data))
                    }
                    is ApiResult.Error -> {
                        if (result.isSessionRejected()) {
                            // Сессию отозвал сервер — расшифрованный токен уже не поможет.
                            biometricLoginStore.clear()
                            refreshBiometricOffer()
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Вход по отпечатку больше не действует " +
                                    "(другой аккаунт или сессия устарела). " +
                                    "Войдите по паролю и снова включите биометрию в Настройках.",
                            )
                        } else {
                            // Сеть или сбой сервера: биовход сохраняем, иначе пользователь
                            // теряет отпечаток из-за одной неудачной попытки.
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Не удалось связаться с сервером. " +
                                    "Проверьте интернет и попробуйте ещё раз.",
                            )
                        }
                    }
                    is ApiResult.Loading -> Unit
                }
            }
        }
    }

    private val _events = MutableSharedFlow<LoginEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun onPhoneChange(raw: String) {
        val national = nationalDigitsFromPhoneField(raw, _uiState.value.phoneNationalDigits)
        _uiState.value = _uiState.value.copy(
            phoneNationalDigits = national,
            phoneError = null
        )
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email.trim(),
            emailError = null,
            validationSummary = null,
            loginHintCode = null,
        )
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
            validationSummary = null,
            loginHintCode = null,
        )
    }

    /** После ввода номера — показать поля email/пароль (API входа по email). */
    fun continueFromPhone() {
        val state = _uiState.value
        if (state.phoneNationalDigits.length != 10) {
            _uiState.value = state.copy(phoneError = "Введите номер полностью")
            return
        }
        _uiState.value = state.copy(credentialsStep = true, phoneError = null)
    }

    fun login() {
        val state = _uiState.value
        var emailError: String? = null
        var passwordError: String? = null
        var hasError = false

        if (state.email.isBlank()) {
            emailError = "Введите email"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            emailError = "Неверный формат email"
            hasError = true
        }

        if (state.password.isNotBlank() && state.password.length < 6) {
            passwordError = "Пароль не менее 6 символов"
            hasError = true
        }

        if (hasError) {
            val summary = listOfNotNull(emailError, passwordError).firstOrNull()
            _uiState.value = state.copy(
                emailError = emailError,
                passwordError = passwordError,
                validationSummary = summary,
                showValidationAttempted = true,
                error = null,
            )
            return
        }

        if (state.password.isBlank()) {
            requestLoginHint(state.email)
            return
        }

        viewModelScope.launch {
            authRepository.login(state.email, state.password).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            error = null,
                            validationSummary = null,
                        )
                    }
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            biometricLoginConfigured = biometricLoginStore.shouldShowBiometricLoginButton(),
                            biometricHardwareReady = biometricLoginStore.canUseDeviceBiometric(),
                        )
                        _events.emit(LoginEvent.Success(result.data))
                    }
                    is ApiResult.Error -> {
                        val hint = when (result.authCode) {
                            "password_not_set" -> LoginHintResult(
                                passwordNotSetHintMessage(),
                                "password_not_set",
                            )
                            else -> LoginHintResult(
                                loginPasswordRequiredMessage(),
                                "password_required",
                            )
                        }
                        applyLoginHint(hint)
                    }
                }
            }
        }
    }

    private fun requestLoginHint(email: String) {
        viewModelScope.launch {
            authRepository.loginHint(email).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            error = null,
                            validationSummary = null,
                        )
                    }
                    is ApiResult.Success -> applyLoginHint(result.data)
                    is ApiResult.Error -> {
                        if (result.authCode == "password_not_set") {
                            applyLoginHint(
                                LoginHintResult(passwordNotSetHintMessage(), "password_not_set"),
                            )
                        } else {
                            requestLoginHintViaLogin(email)
                        }
                    }
                }
            }
        }
    }

    /** Если login-hint ещё не задеплоен — проверяем через POST /login с пустым паролем. */
    private fun requestLoginHintViaLogin(email: String) {
        viewModelScope.launch {
            authRepository.login(email, "").collect { result ->
                when (result) {
                    is ApiResult.Loading -> Unit
                    is ApiResult.Success -> applyLoginHint(
                        LoginHintResult(loginPasswordRequiredMessage(), "password_required"),
                    )
                    is ApiResult.Error -> {
                        val hint = when (result.authCode) {
                            "password_not_set" -> LoginHintResult(
                                passwordNotSetHintMessage(),
                                "password_not_set",
                            )
                            else -> LoginHintResult(
                                loginPasswordRequiredMessage(),
                                "password_required",
                            )
                        }
                        applyLoginHint(hint)
                    }
                }
            }
        }
    }

    private fun applyLoginHint(hint: LoginHintResult) {
        when (hint.code) {
            "password_not_set" -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    validationSummary = hint.message,
                    loginHintCode = "password_not_set",
                    passwordError = null,
                    showValidationAttempted = true,
                    error = null,
                )
            }
            "email_unknown" -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    validationSummary = "Аккаунт не найден. Зарегистрируйтесь.",
                    loginHintCode = "email_unknown",
                    passwordError = null,
                    showValidationAttempted = true,
                    error = null,
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    validationSummary = null,
                    loginHintCode = hint.code.takeIf { it.isNotBlank() },
                    passwordError = loginPasswordRequiredMessage(),
                    showValidationAttempted = true,
                    error = null,
                )
            }
        }
    }

    fun loginWithSberId() {
        viewModelScope.launch {
            val verifier = SberPkce.createCodeVerifier()
            val challenge = SberPkce.createCodeChallenge(verifier)
            authRepository.getSberAuthorizeUrl(
                codeChallenge = challenge,
                redirectUri = sberRedirectUri,
                appBridgeUri = BuildConfig.APP_AUTH_BRIDGE_URI,
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is ApiResult.Success -> {
                        sberCodeVerifier = verifier
                        authFlowStore.savePendingSberVerifier(verifier)
                        _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                        _events.emit(LoginEvent.OpenExternalUrl(result.data))
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun handleSberDeepLink(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val desc = uri.getQueryParameter("error_description")
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = desc ?: "Сбер ID: $error",
            )
            return
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Не удалось завершить вход через Сбер ID. Попробуйте ещё раз.",
            )
            return
        }

        viewModelScope.launch {
            var verifier = sberCodeVerifier
            if (verifier.isNullOrBlank()) {
                verifier = authFlowStore.peekPendingSberVerifier()
            }
            if (verifier.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Сессия Сбер ID истекла. Нажмите «Войти через Сбер ID» ещё раз.",
                )
                return@launch
            }

            authRepository.loginWithSberCode(
                code = code,
                codeVerifier = verifier,
                redirectUri = sberRedirectUri,
                state = state,
                clubId = authFlowStore.peekPendingRegistrationClubId(),
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    }
                    is ApiResult.Success -> {
                        sberCodeVerifier = null
                        authFlowStore.clearPendingSberVerifier()
                        authFlowStore.consumePendingRegistrationClubId()
                        val welcome = if (welcomeAfterSberLogin) {
                            welcomeAfterSberLogin = false
                            "Аккаунт создан, добро пожаловать!"
                        } else {
                            null
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            hasCompletedRegistration = true,
                            biometricLoginConfigured = biometricLoginStore.shouldShowBiometricLoginButton(),
                            biometricHardwareReady = biometricLoginStore.canUseDeviceBiometric(),
                        )
                        _events.emit(LoginEvent.Success(result.data, welcomeMessage = welcome))
                    }
                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    fun onOtpChannelChange(channel: String) {
        val available = _uiState.value.otpChannelAvailable
        if (available.isNotEmpty() && available[channel] == false) {
            _uiState.value = _uiState.value.copy(
                error = "Этот канал ещё не подключён. Выберите другой или войдите по почте / Сбер ID.",
            )
            return
        }
        _uiState.value = _uiState.value.copy(otpChannel = channel, error = null)
    }

    fun onOtpCodeChange(code: String) {
        val digits = code.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(otpCode = digits, otpError = null)
        if (digits.length == 6) {
            verifyOtp()
        }
    }

    fun requestOtp() {
        val phone = phoneForApi(_uiState.value.phoneNationalDigits)
        if (phone.isEmpty()) {
            _uiState.value = _uiState.value.copy(phoneError = "Введите полный номер телефона")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, phoneError = null)
            when (val r = authRepository.requestOtp(phone, _uiState.value.otpChannel)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpStep = LoginOtpStep.CODE,
                        otpInstruction = r.data.instruction,
                        otpDeeplink = r.data.deeplink,
                        otpDevCode = r.data.devCode,
                        resendSecondsLeft = r.data.resendAfterSec.coerceAtLeast(20),
                        otpCode = "",
                    )
                    tickResend()
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = r.message)
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val phone = phoneForApi(state.phoneNationalDigits)
        if (state.otpCode.length != 6) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, otpError = null)
            when (val r = authRepository.verifyOtp(phone, state.otpCode)) {
                is ApiResult.Success -> {
                    val body = r.data
                    if (body.registrationRequired && !body.otpTicket.isNullOrBlank()) {
                        authFlowStore.saveOtpRegistration(body.otpTicket, body.phone ?: phone)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(LoginEvent.NeedPhoneRegistration)
                    } else if (body.user != null) {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(LoginEvent.Success(body.user))
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, otpError = "Не удалось войти")
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        otpError = r.message,
                        otpCode = "",
                        otpShakeNonce = state.otpShakeNonce + 1,
                    )
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    fun toggleEmailLogin() {
        _uiState.update { it.copy(showEmailLogin = !it.showEmailLogin) }
    }

    fun backToPhoneStep() {
        _uiState.value = _uiState.value.copy(otpStep = LoginOtpStep.PHONE, otpCode = "", otpError = null)
    }

    private fun tickResend() {
        viewModelScope.launch {
            while (_uiState.value.resendSecondsLeft > 0) {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(resendSecondsLeft = (it.resendSecondsLeft - 1).coerceAtLeast(0)) }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class LoginUiState(
    val phoneNationalDigits: String = "",
    val phoneError: String? = null,
    val credentialsStep: Boolean = false,
    val otpStep: LoginOtpStep = LoginOtpStep.PHONE,
    val otpChannel: String = "telegram",
    val otpChannelAvailable: Map<String, Boolean> = emptyMap(),
    val otpChannelsLoaded: Boolean = false,
    val otpCode: String = "",
    val otpError: String? = null,
    val otpShakeNonce: Int = 0,
    val supportEmail: String? = null,
    val supportPhone: String? = null,
    val otpInstruction: String? = null,
    val otpDeeplink: String? = null,
    val otpDevCode: String? = null,
    val resendSecondsLeft: Int = 0,
    val showEmailLogin: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val validationSummary: String? = null,
    val loginHintCode: String? = null,
    val showValidationAttempted: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val clubBrandName: String = Brand.name,
    val hasCompletedRegistration: Boolean = false,
    val biometricLoginConfigured: Boolean = false,
    val biometricHardwareReady: Boolean = false,
)

sealed class LoginEvent {
    data class Success(
        val user: User,
        val welcomeMessage: String? = null,
    ) : LoginEvent()
    data class OpenExternalUrl(val url: String) : LoginEvent()
    data object NeedPhoneRegistration : LoginEvent()
}

enum class LoginOtpStep { PHONE, CODE }
