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
import com.example.staffapp.ui.auth.RoleOptionUi
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null

    private val roleOptions = listOf(
        RoleOptionUi("Тренер", "ROLE_TRAINER"),
        RoleOptionUi("Менеджер", "ROLE_MANAGER"),
        RoleOptionUi("Финансы", "ROLE_FINANCE"),
        RoleOptionUi("Наблюдатель", "ROLE_VIEWER"),
        RoleOptionUi("Поддержка", "ROLE_SUPPORT"),
        RoleOptionUi("Администратор", "ROLE_ADMIN"),
        RoleOptionUi("Суперадминистратор", "ROLE_SUPER_ADMIN"),
    )

    private var uiState by mutableStateOf(
        LoginUiState(roles = roleOptions, selectedRole = roleOptions.first()),
    )

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
                    onRoleSelected = { uiState = uiState.copy(selectedRole = it) },
                    onLogin = { runLogin() },
                    onRegister = { runRegister() },
                )
            }
        }

        StaffNotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        if (session != null) {
            runAsync("Проверяем доступ...") {
                val config = executeWithRefresh { token -> apiClient.loadConfig(token) }
                store.saveConfig(config)
                openWorkScreen()
                "Готово"
            }
        }
    }

    private fun runRegister() {
        runAsync("Регистрация...") {
            session = apiClient.register(
                email = uiState.email.trim(),
                name = uiState.name.trim(),
                password = uiState.password,
                role = uiState.selectedRole?.role ?: roleOptions.first().role,
            )
            session?.let { store.saveSession(it) }
            store.clearConfig()
            val config = executeWithRefresh { token -> apiClient.loadConfig(token) }
            store.saveConfig(config)
            StaffPushRegistrar.registerIfLoggedIn(this)
            openWorkScreen()
            "Зарегистрирован и выполнен вход"
        }
    }

    private fun runLogin() {
        runAsync("Вход...") {
            session = apiClient.login(
                email = uiState.email.trim(),
                password = uiState.password,
            )
            session?.let { store.saveSession(it) }
            store.clearConfig()
            val config = executeWithRefresh { token -> apiClient.loadConfig(token) }
            store.saveConfig(config)
            runOnUiThread {
                uiState = uiState.copy(
                    configSummary = "Роли: ${config.roles.joinToString { UiLabels.roleTitle(it) }}\nДоступы загружены",
                )
            }
            StaffPushRegistrar.registerIfLoggedIn(this)
            openWorkScreen()
            "Выполнен вход"
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            42,
        )
    }
}
