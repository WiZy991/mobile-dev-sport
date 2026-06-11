package com.example.staffapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.admin.AdminSectionScreen
import com.example.staffapp.ui.admin.AdminSectionUi
import com.example.staffapp.ui.theme.StaffTheme
import com.example.staffapp.ui.work.ActionUi
import com.example.staffapp.ui.work.ListCardUi
import com.example.staffapp.ui.work.MetricUi
import kotlin.concurrent.thread

class AdminSectionActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null
    private var section: String = ""

    private var uiState by mutableStateOf(AdminSectionUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        section = intent.getStringExtra(EXTRA_SECTION).orEmpty()

        uiState = AdminSectionUi(title = UiLabels.sectionTitle(section))

        setContent {
            StaffTheme {
                AdminSectionScreen(
                    state = uiState,
                    onBack = { finish() },
                    onAction = { handleAction(it) },
                    onItemClick = { openItem(it) },
                    onRetry = { loadSection() },
                )
            }
        }

        loadSection()
    }

    private fun handleAction(actionId: String) {
        when (actionId) {
            "shortcut:schedule" -> openWorkTab(R.id.nav_schedule)
            "shortcut:clients" -> openWorkTab(R.id.nav_clients)
            "shortcut:support" -> openWorkTab(R.id.nav_support)
        }
    }

    private fun openWorkTab(tab: Int) {
        startActivity(
            Intent(this, WorkActivity::class.java)
                .putExtra(WorkActivity.EXTRA_INITIAL_TAB, tab),
        )
        finish()
    }

    private fun loadSection() {
        if (section.isBlank()) {
            uiState = uiState.copy(loading = false, error = "Раздел не указан")
            return
        }
        uiState = uiState.copy(loading = true, error = null)
        thread {
            try {
                val cards = withRefresh { token ->
                    apiClient.loadSectionData(token, "admin", section)
                }
                val items = withRefresh { token -> apiClient.loadList(token, section) }
                runOnUiThread {
                    uiState = AdminSectionUi(
                        title = UiLabels.sectionTitle(section),
                        metrics = cards.cards.map { (key, value) ->
                            MetricUi(UiLabels.metricTitle(key), value.toString())
                        },
                        items = items.map { feedToCard(it) },
                        shortcuts = sectionShortcuts(),
                        summary = if (items.isEmpty()) {
                            "Записей в разделе пока нет"
                        } else {
                            "Записей: ${items.size}"
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

    private fun sectionShortcuts(): List<ActionUi> = when (section) {
        "schedule" -> listOf(ActionUi("shortcut:schedule", "Открыть календарь расписания"))
        "clients" -> listOf(ActionUi("shortcut:clients", "Открыть поиск клиентов"))
        "app_support" -> listOf(ActionUi("shortcut:support", "Открыть обращения с фильтрами"))
        else -> emptyList()
    }

    private fun feedToCard(item: FeedListItem): ListCardUi {
        val clientId = if (item.refType == "client") item.id else null
        val ticketId = if (item.refType == "ticket") item.id else null
        return ListCardUi(
            title = item.title,
            subtitle = item.subtitle,
            meta = item.meta,
            clientId = clientId,
            ticketId = ticketId,
            refType = item.refType,
            feedId = item.id,
        )
    }

    private fun openItem(item: ListCardUi) {
        when (item.refType) {
            "client" -> {
                val clientId = item.clientId ?: item.feedId ?: return
                startActivity(
                    Intent(this, ClientDetailActivity::class.java)
                        .putExtra(ClientDetailActivity.EXTRA_CLIENT_ID, clientId),
                )
            }
            "ticket" -> openWorkTab(R.id.nav_support)
        }
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

    companion object {
        const val EXTRA_SECTION = "extra_admin_section"
    }
}
