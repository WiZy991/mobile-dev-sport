package com.example.staffapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.feedback.FeedbackScreenState
import com.example.staffapp.ui.feedback.StaffFeedbackScreen
import com.example.staffapp.ui.theme.StaffTheme
import kotlin.concurrent.thread

class StaffFeedbackActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null

    private var uiState by mutableStateOf(FeedbackScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        if (session == null) {
            finish()
            return
        }

        setContent {
            StaffTheme {
                StaffFeedbackScreen(
                    state = uiState,
                    onBack = { finish() },
                    onSubjectChange = { uiState = uiState.copy(subject = it) },
                    onMessageChange = { uiState = uiState.copy(message = it) },
                    onCategoryChange = { uiState = uiState.copy(category = it) },
                    onSend = { send() },
                    onRefresh = { load() },
                )
            }
        }
        load()
    }

    private fun load() {
        uiState = uiState.copy(loading = true, errorMessage = null)
        thread {
            try {
                val tickets = withRefresh { apiClient.loadMyFeedbackTickets(it) }
                runOnUiThread {
                    uiState = uiState.copy(tickets = tickets, loading = false)
                }
            } catch (e: Exception) {
                val missing = e.message.orEmpty().lowercase().let {
                    it.contains("404") || it.contains("no route found")
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        tickets = emptyList(),
                        loading = false,
                        // Без красного баннера на каждый 404: форма отправки остаётся доступной.
                        errorMessage = if (missing) null else UserFacingError.message(e),
                        statusMessage = if (missing) {
                            "История обращений появится после обновления CRM на сервере."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private fun send() {
        uiState = uiState.copy(sending = true, errorMessage = null, statusMessage = null)
        thread {
            try {
                withRefresh {
                    apiClient.createFeedbackTicket(
                        it,
                        subject = uiState.subject.trim(),
                        message = uiState.message.trim(),
                        category = uiState.category,
                    )
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        sending = false,
                        subject = "",
                        message = "",
                        statusMessage = "Обращение отправлено",
                    )
                    load()
                }
            } catch (e: Exception) {
                val missing = e.message.orEmpty().lowercase().let {
                    it.contains("404") || it.contains("no route found")
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        sending = false,
                        errorMessage = if (missing) {
                            "На сервере ещё нет API обратной связи. Нужно задеплоить CRM и миграции."
                        } else {
                            UserFacingError.message(e)
                        },
                    )
                }
            }
        }
    }

    private fun <T> withRefresh(action: (token: String) -> T): T {
        val current = session ?: throw IllegalStateException("Нет сессии")
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
