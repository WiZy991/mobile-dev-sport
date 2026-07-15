package com.fitnessclub.app.ui.screens.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FeedbackRequest
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.local.AppLanguage
import com.fitnessclub.app.data.local.AppSettingsStore
import com.fitnessclub.app.data.local.BiometricLoginCoordinator
import com.fitnessclub.app.data.local.BiometricLoginStore
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.local.ThemeMode
import com.fitnessclub.app.data.model.NotificationSettings
import com.fitnessclub.app.data.repository.NotificationSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val feedbackSuccess: Boolean = false,
    val feedbackError: String? = null,
    val cacheCleared: Boolean = false,
    val biometricLoginEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val notificationsLoading: Boolean = true,
    val notificationsSaving: Boolean = false,
    val notificationsError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: FitnessApi,
    private val tokenManager: TokenManager,
    private val biometricLoginStore: BiometricLoginStore,
    private val appSettingsStore: AppSettingsStore,
    private val notificationSettingsRepository: NotificationSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshBiometricUi()
        observeAppSettings()
        loadNotificationSettings()
    }

    private fun observeAppSettings() {
        viewModelScope.launch {
            appSettingsStore.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            appSettingsStore.appLanguage.collect { language ->
                _uiState.update { it.copy(appLanguage = language) }
            }
        }
    }

    fun refreshBiometricUi() {
        _uiState.update { it.copy(biometricLoginEnabled = biometricLoginStore.hasStoredCredential()) }
    }

    fun loadNotificationSettings() {
        viewModelScope.launch {
            notificationSettingsRepository.load().collect { result ->
                when (result) {
                    is ApiResult.Loading -> _uiState.update {
                        it.copy(notificationsLoading = true, notificationsError = null)
                    }
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            notificationsLoading = false,
                            notificationSettings = result.data,
                            notificationsError = null,
                        )
                    }
                    is ApiResult.Error -> _uiState.update {
                        it.copy(
                            notificationsLoading = false,
                            notificationsError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun setPushEnabled(enabled: Boolean) = updateNotificationSettings(
        _uiState.value.notificationSettings.copy(pushEnabled = enabled)
    )

    fun setEmailEnabled(enabled: Boolean) = updateNotificationSettings(
        _uiState.value.notificationSettings.copy(emailEnabled = enabled)
    )

    fun setTrainingReminders(enabled: Boolean) = updateNotificationSettings(
        _uiState.value.notificationSettings.copy(trainingReminders = enabled)
    )

    fun setScheduleChanges(enabled: Boolean) = updateNotificationSettings(
        _uiState.value.notificationSettings.copy(scheduleChanges = enabled)
    )

    fun setPromoNotifications(enabled: Boolean) = updateNotificationSettings(
        _uiState.value.notificationSettings.copy(promoNotifications = enabled)
    )

    private fun updateNotificationSettings(settings: NotificationSettings) {
        val previous = _uiState.value.notificationSettings
        _uiState.update { it.copy(notificationSettings = settings, notificationsError = null) }

        viewModelScope.launch {
            notificationSettingsRepository.save(settings).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _uiState.update { it.copy(notificationsSaving = true) }
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            notificationsSaving = false,
                            notificationSettings = result.data,
                        )
                    }
                    is ApiResult.Error -> _uiState.update {
                        it.copy(
                            notificationsSaving = false,
                            notificationSettings = previous,
                            notificationsError = result.message,
                        )
                    }
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
            }
            _uiState.update { it.copy(cacheCleared = true) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsStore.setThemeMode(mode)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            appSettingsStore.setAppLanguage(language)
        }
    }

    fun submitFeedback(rating: Int, comment: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackError = null) }
            try {
                val res = api.submitFeedback(FeedbackRequest(rating = rating, comment = comment, type = "general"))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(feedbackSuccess = true) }
                    onSuccess()
                } else {
                    _uiState.update { it.copy(feedbackError = "Ошибка отправки") }
                    onError("Ошибка отправки")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackError = e.message) }
                onError(e.message ?: "Ошибка сети")
            }
        }
    }

    fun onBiometricLoginSwitch(
        enabled: Boolean,
        activity: FragmentActivity?,
        onMessage: (String) -> Unit,
    ) {
        if (!enabled) {
            biometricLoginStore.clear()
            refreshBiometricUi()
            return
        }
        val act = activity ?: run {
            onMessage("Не удалось открыть окно биометрии")
            return
        }
        if (!biometricLoginStore.canUseDeviceBiometric()) {
            onMessage("На устройстве недоступна биометрия")
            return
        }
        viewModelScope.launch {
            val rt = tokenManager.getRefreshToken()
            if (rt.isNullOrBlank()) {
                onMessage("Войдите в аккаунт ещё раз, затем включите отпечаток")
                return@launch
            }
            withContext(Dispatchers.Main) {
                BiometricLoginCoordinator.startEncryptPrompt(act, biometricLoginStore, rt) { ok, err ->
                    if (ok) {
                        refreshBiometricUi()
                        onMessage("Вход по отпечатку включён")
                    } else if (!err.isNullOrBlank()) {
                        onMessage(err)
                    }
                }
            }
        }
    }
}
