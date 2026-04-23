package com.fitnessclub.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.local.BiometricLoginCoordinator
import com.fitnessclub.app.data.local.BiometricLoginStore
import com.fitnessclub.app.data.local.TokenManager
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
    private val authRepository: AuthRepository,
    private val biometricLoginStore: BiometricLoginStore,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        refreshBiometricOffer()
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
                error = "Сначала войдите в приложение (номер телефона и пароль). Затем в Настройки → Безопасность включите «Биометрию».",
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
        BiometricLoginCoordinator.startDecryptPrompt(activity, biometricLoginStore) { rt, err ->
            if (rt == null) {
                if (!err.isNullOrBlank()) {
                    viewModelScope.launch {
                        _uiState.value = _uiState.value.copy(error = err, isLoading = false)
                    }
                }
                return@startDecryptPrompt
            }
            viewModelScope.launch {
                authRepository.restoreSessionWithRefreshToken(rt).collect { result ->
                    when (result) {
                        is ApiResult.Loading -> {
                            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                        }
                        is ApiResult.Success -> {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            _events.emit(LoginEvent.Success(result.data))
                        }
                        is ApiResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun onPhoneChange(raw: String) {
        val national = normalizeRussianNationalDigits(raw)
        _uiState.value = _uiState.value.copy(
            phoneNationalDigits = national,
            phoneError = null
        )
    }

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email.trim(), emailError = null)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password, passwordError = null)
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

        if (state.password.isBlank()) {
            passwordError = "Введите пароль"
            hasError = true
        } else if (state.password.length < 6) {
            passwordError = "Пароль не менее 6 символов"
            hasError = true
        }

        if (hasError) {
            _uiState.value = state.copy(emailError = emailError, passwordError = passwordError)
            return
        }

        viewModelScope.launch {
            authRepository.login(state.email, state.password).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.value = _uiState.value.copy(isLoading = true)
                    }
                    is ApiResult.Success -> {
                        val rebindRefresh = if (biometricLoginStore.hasStoredCredential()) {
                            tokenManager.getRefreshToken()?.trim()?.takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            biometricLoginConfigured = biometricLoginStore.shouldShowBiometricLoginButton(),
                            biometricHardwareReady = biometricLoginStore.canUseDeviceBiometric(),
                        )
                        _events.emit(LoginEvent.Success(result.data, rebindRefresh))
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

    /**
     * После входа по паролю сервер мог выдать другой refresh, чем в SecureStore.
     * Один раз перешифровываем актуальный refresh тем же сценарием, что в настройках.
     */
    fun reEncryptBiometricAfterPasswordLogin(
        activity: FragmentActivity,
        refreshToken: String?,
        onFinished: () -> Unit,
    ) {
        if (refreshToken.isNullOrBlank()) {
            onFinished()
            return
        }
        BiometricLoginCoordinator.startEncryptPrompt(activity, biometricLoginStore, refreshToken) { _, _ ->
            onFinished()
        }
    }
}

data class LoginUiState(
    val phoneNationalDigits: String = "",
    val phoneError: String? = null,
    val credentialsStep: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** В настройках сохранён вход по отпечатку (есть зашифрованный refresh). */
    val biometricLoginConfigured: Boolean = false,
    /** Устройство сообщает, что можно показать BiometricPrompt. */
    val biometricHardwareReady: Boolean = false,
)

sealed class LoginEvent {
    data class Success(
        val user: User,
        /** Если отпечаток уже был включён — заново зашифровать refresh после входа по паролю. */
        val refreshToReEncryptForBiometric: String? = null,
    ) : LoginEvent()
}
