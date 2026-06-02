package com.example.staffapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class ClientDetailActivity : AppCompatActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private lateinit var contentView: TextView
    private lateinit var statusView: TextView
    private lateinit var callButton: Button
    private var session: StaffSession? = null
    private var phone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_detail)
        StaffUi.enableEdgeToEdge(this)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        val appBar = findViewById<AppBarLayout>(R.id.staffAppBar)
        val toolbar = findViewById<MaterialToolbar>(R.id.staffToolbar)
        StaffUi.applyAppBarInsets(appBar)
        StaffUi.setupToolbar(
            toolbar = toolbar,
            title = "Клиент",
            showBack = true,
            showLogout = false,
            onBack = { finish() },
            onLogout = null,
        )
        session = store.loadSession()
        contentView = findViewById(R.id.clientContentView)
        statusView = findViewById(R.id.clientStatusView)
        callButton = findViewById(R.id.clientCallButton)

        val clientId = intent.getIntExtra(EXTRA_CLIENT_ID, 0)
        callButton.setOnClickListener {
            val number = phone?.trim().orEmpty()
            if (number.isNotBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            }
        }

        if (clientId <= 0) {
            contentView.text = "Клиент не найден"
            return
        }
        loadClient(clientId)
    }

    private fun loadClient(clientId: Int) {
        statusView.visibility = View.VISIBLE
        statusView.text = "Загрузка карточки..."
        thread {
            try {
                val detail = withRefresh { token -> apiClient.loadClientDetail(token, clientId) }
                runOnUiThread { renderClient(detail) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.visibility = View.VISIBLE
                    statusView.text = UserFacingError.message(e)
                }
            }
        }
    }

    private fun renderClient(client: ClientDetail) {
        findViewById<MaterialToolbar>(R.id.staffToolbar).title = client.name
        phone = client.phone
        callButton.visibility = if (client.phone.isNotBlank()) View.VISIBLE else View.GONE

        contentView.text = buildString {
            append("${client.name}\n")
            if (client.email.isNotBlank()) append("${client.email}\n")
            if (client.phone.isNotBlank()) append("${client.phone}\n")
            if (client.isBlocked) append("\n⚠ Клиент заблокирован\n")
            append("\nБонусы: ${client.bonusPoints}\n")

            val sub = client.subscription
            append("\nАбонемент:\n")
            if (sub == null) {
                append("Нет активного абонемента\n")
            } else {
                append("${sub.plan} · ${sub.status}\n")
                if (!sub.endDate.isNullOrBlank()) append("До ${sub.endDate}\n")
                if (sub.visitsTotal > 0) {
                    append("Визиты: ${sub.visitsUsed}/${sub.visitsTotal}\n")
                }
            }

            append("\nПоследние записи:\n")
            if (client.recentBookings.isEmpty()) {
                append("Нет записей\n")
            } else {
                client.recentBookings.forEach { row ->
                    append("• ${row.title}\n  ${row.meta}\n")
                }
            }

            append("\nОбращения:\n")
            if (client.recentTickets.isEmpty()) {
                append("Нет обращений\n")
            } else {
                client.recentTickets.forEach { row ->
                    append("• ${row.title}\n  ${row.meta}\n")
                }
            }
        }
        statusView.visibility = View.GONE
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
