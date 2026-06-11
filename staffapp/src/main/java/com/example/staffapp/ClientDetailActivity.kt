package com.example.staffapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.client.ClientDetailScreen
import com.example.staffapp.ui.client.ClientDetailUi
import com.example.staffapp.ui.theme.StaffTheme
import com.example.staffapp.ui.work.ListCardUi
import kotlin.concurrent.thread

class ClientDetailActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private var session: StaffSession? = null
    private var phone: String? = null
    private var uiState by mutableStateOf(ClientDetailUi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()

        val clientId = intent.getIntExtra(EXTRA_CLIENT_ID, 0)
        setContent {
            StaffTheme {
                ClientDetailScreen(
                    state = uiState,
                    onBack = { finish() },
                    onRetry = {
                        val clientId = intent.getIntExtra(EXTRA_CLIENT_ID, 0)
                        if (clientId > 0) loadClient(clientId)
                    },
                    onCall = {
                        val number = phone?.trim().orEmpty()
                        if (number.isNotBlank()) {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                        }
                    },
                )
            }
        }

        if (clientId <= 0) {
            uiState = ClientDetailUi(loading = false, error = "Клиент не найден")
            return
        }
        loadClient(clientId)
    }

    private fun loadClient(clientId: Int) {
        uiState = ClientDetailUi(loading = true)
        thread {
            try {
                val detail = withRefresh { token -> apiClient.loadClientDetail(token, clientId) }
                runOnUiThread { renderClient(detail) }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState = ClientDetailUi(loading = false, error = UserFacingError.message(e))
                }
            }
        }
    }

    private fun renderClient(client: ClientDetail) {
        phone = client.phone
        val sub = client.subscription
        val subTitle = sub?.plan.orEmpty()
        val subMeta = buildString {
            if (sub != null) {
                append("${sub.status}")
                if (!sub.endDate.isNullOrBlank()) append(" · до ${sub.endDate}")
                if (sub.visitsTotal > 0) append("\nВизиты: ${sub.visitsUsed}/${sub.visitsTotal}")
            }
        }
        uiState = ClientDetailUi(
            title = client.name,
            name = client.name,
            email = client.email,
            phone = client.phone,
            bonusPoints = client.bonusPoints,
            isBlocked = client.isBlocked,
            subscriptionTitle = subTitle,
            subscriptionMeta = subMeta,
            bookings = client.recentBookings.map {
                ListCardUi(title = it.title, meta = it.meta)
            },
            tickets = client.recentTickets.map {
                ListCardUi(title = it.title, meta = it.meta)
            },
            loading = false,
            showCallButton = client.phone.isNotBlank(),
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

    companion object {
        const val EXTRA_CLIENT_ID = "extra_client_id"
    }
}
