package com.example.staffapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var apiClient: StaffApiClient

    private lateinit var emailInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var roleSpinner: Spinner
    private lateinit var configSummaryView: TextView
    private lateinit var statusView: TextView
    private val roleOptions = listOf(
        RoleOption("Тренер", "ROLE_TRAINER"),
        RoleOption("Менеджер", "ROLE_MANAGER"),
        RoleOption("Финансы", "ROLE_FINANCE"),
        RoleOption("Наблюдатель", "ROLE_VIEWER"),
        RoleOption("Поддержка", "ROLE_SUPPORT"),
        RoleOption("Администратор", "ROLE_ADMIN"),
        RoleOption("Суперадминистратор", "ROLE_SUPER_ADMIN"),
    )

    private var session: StaffSession? = null
    private lateinit var store: StaffSessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        StaffUi.enableEdgeToEdge(this, lightStatusBar = true)
        val mainScroll = findViewById<android.view.View>(R.id.mainScroll)
        ViewCompat.setOnApplyWindowInsetsListener(mainScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + 16, bars.top + 16, bars.right + 16, bars.bottom + 16)
            insets
        }
        ViewCompat.requestApplyInsets(mainScroll)

        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()

        emailInput = findViewById(R.id.emailInput)
        nameInput = findViewById(R.id.nameInput)
        passwordInput = findViewById(R.id.passwordInput)
        roleSpinner = findViewById(R.id.roleSpinner)
        configSummaryView = findViewById(R.id.configSummaryView)
        statusView = findViewById(R.id.statusView)

        setupRolePicker()

        findViewById<Button>(R.id.registerButton).setOnClickListener { runRegister() }
        findViewById<Button>(R.id.loginButton).setOnClickListener { runLogin() }

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

    private fun setupRolePicker() {
        roleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roleOptions.map { it.title }
        )
    }

    private fun runRegister() {
        runAsync("Регистрация...") {
            session = apiClient.register(
                email = emailInput.text.toString().trim(),
                name = nameInput.text.toString().trim(),
                password = passwordInput.text.toString(),
                role = selectedRoleCode(),
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
                email = emailInput.text.toString().trim(),
                password = passwordInput.text.toString(),
            )
            session?.let { store.saveSession(it) }
            store.clearConfig()
            val config = executeWithRefresh { token -> apiClient.loadConfig(token) }
            store.saveConfig(config)
            renderConfigSummary(config)
            StaffPushRegistrar.registerIfLoggedIn(this)
            openWorkScreen()
            "Выполнен вход"
        }
    }

    private fun renderConfigSummary(config: RoleConfig) {
        runOnUiThread {
            configSummaryView.text = buildString {
                append("Роли: ${config.roles.joinToString { UiLabels.roleTitle(it) }}\n")
                append("Доступы загружены автоматически")
            }
        }
    }

    private fun runAsync(progressText: String, action: () -> String) {
        statusView.text = progressText
        thread {
            try {
                val result = action()
                runOnUiThread { statusView.text = result }
            } catch (e: Exception) {
                runOnUiThread { statusView.text = UserFacingError.message(e) }
            }
        }
    }

    private fun <T> executeWithRefresh(action: (token: String) -> T): T {
        val current = session
        if (current == null) {
            throw IllegalStateException("Сначала выполните вход или регистрацию.")
        }
        return try {
            action(current.accessToken)
        } catch (e: IllegalStateException) {
            if (!e.message.orEmpty().contains("401")) {
                throw e
            }
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

    private fun selectedRoleCode(): String {
        val idx = roleSpinner.selectedItemPosition.coerceAtLeast(0)
        return roleOptions.getOrElse(idx) { roleOptions.first() }.code
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

    private data class RoleOption(val title: String, val code: String)
}
