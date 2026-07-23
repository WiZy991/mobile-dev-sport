package com.example.staffapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.staffapp.ui.auth.LoginScreen
import com.example.staffapp.ui.auth.LoginUiState
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null

    private var uiState by mutableStateOf(LoginUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()

        setContent {
            StaffTheme {
                LoginScreen(
                    state = uiState,
                    onEmailChange = { uiState = uiState.copy(email = it) },
                    onNameChange = { uiState = uiState.copy(name = it) },
                    onPasswordChange = { uiState = uiState.copy(password = it) },
                    onRoleSelected = {},
                    onLogin = { runLogin() },
                    onRegister = { runRegister() },
                )
            }
        }

        StaffNotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        if (session != null) {
            runAsync("Проверяем доступ...") {
                routeAfterAuth()
                "Готово"
            }
        }
    }

    private fun runRegister() {
        runAsync("Регистрация...") {
            val result = apiClient.register(
                email = uiState.email.trim(),
                name = uiState.name.trim(),
                password = uiState.password,
            )
            session = result.session
            store.saveSession(result.session)
            store.clearConfig()
            StaffPushRegistrar.registerIfLoggedIn(this)
            routeAfterAuth(result.onboarding)
            "Заявка отправлена"
        }
    }

    private fun runLogin() {
        runAsync("Вход...") {
            val result = apiClient.login(
                email = uiState.email.trim(),
                password = uiState.password,
            )
            session = result.session
            store.saveSession(result.session)
            store.clearConfig()
            StaffPushRegistrar.registerIfLoggedIn(this)
            routeAfterAuth(result.onboarding)
            "Выполнен вход"
        }
    }

    private fun routeAfterAuth(prefetched: StaffOnboarding? = null) {
        val onboarding = prefetched ?: executeWithRefresh { apiClient.loadOnboarding(it) }
        if (onboarding.status == "active") {
            val config = executeWithRefresh { token -> apiClient.loadConfig(token) }
            store.saveConfig(config)
            openWorkScreen()
        } else {
            openOnboardingScreen()
        }
    }

    private fun runAsync(progressText: String, action: () -> String) {
        uiState = uiState.copy(isLoading = true, statusMessage = progressText, errorMessage = null)
        thread {
            try {
                action()
                runOnUiThread {
                    uiState = uiState.copy(isLoading = false, statusMessage = null)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        isLoading = false,
                        statusMessage = null,
                        errorMessage = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun <T> executeWithRefresh(action: (token: String) -> T): T {
        val current = session
            ?: throw IllegalStateException("Сначала выполните вход или регистрацию.")
        return try {
            action(current.accessToken)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().contains("401")) throw e
            val refreshed = apiClient.refresh(current.refreshToken)
            session = refreshed
            store.saveSession(refreshed)
            action(refreshed.accessToken)
        }
    }

    private fun openWorkScreen() {
        runOnUiThread {
            startActivity(Intent(this, WorkActivity::class.java))
            finish()
        }
    }

    private fun openOnboardingScreen() {
        runOnUiThread {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }
}
