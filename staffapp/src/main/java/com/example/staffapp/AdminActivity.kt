package com.example.staffapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.admin.AdminHubScreen
import com.example.staffapp.ui.admin.AdminHubUi
import com.example.staffapp.ui.admin.AdminSectionRowUi
import com.example.staffapp.ui.theme.StaffTheme
import com.example.staffapp.ui.work.MetricUi
import com.example.staffapp.ui.work.SectionHints
import kotlin.concurrent.thread

class AdminActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null
    private var config: RoleConfig? = null
    private var adminMenu: Map<String, String> = emptyMap()

    private var uiState by mutableStateOf(AdminHubUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        config = store.loadConfig()

        setContent {
            StaffTheme {
                AdminHubScreen(
                    state = uiState,
                    onBack = { finish() },
                    onLogout = { logout() },
                    onSectionClick = { openSection(it) },
                    onRetry = { loadData() },
                )
            }
        }

        loadData()
    }

    private fun logout() {
        store.clearAll()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun loadData() {
        uiState = uiState.copy(loading = true, error = null)
        thread {
            try {
                if (config?.adminSections.isNullOrEmpty()) {
                    throw IllegalStateException("Нет доступа к админке")
                }
                val data = withRefresh { token -> apiClient.loadAdminData(token) }
                adminMenu = data.adminMenu
                runOnUiThread {
                    uiState = AdminHubUi(
                        canWrite = data.canWrite,
                        metrics = data.widgets.map { (key, value) ->
                            MetricUi(UiLabels.metricTitle(key), value.toString())
                        },
                        sections = data.adminSections.map { section ->
                            AdminSectionRowUi(
                                key = section,
                                title = adminMenu[section] ?: UiLabels.sectionTitle(section),
                                hint = SectionHints.forSection(section),
                            )
                        },
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        loading = false,
                        error = UserFacingError.message(e),
                    )
                }
            }
        }
    }

    private fun openSection(section: String) {
        startActivity(
            Intent(this, AdminSectionActivity::class.java)
                .putExtra(AdminSectionActivity.EXTRA_SECTION, section),
        )
    }

    private fun <T> withRefresh(action: (String) -> T): T {
        val current = session ?: throw IllegalStateException("Сессия не найдена")
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
}
