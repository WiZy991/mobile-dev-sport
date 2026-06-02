package com.example.staffapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class AdminSectionActivity : AppCompatActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private lateinit var titleView: MaterialToolbar
    private lateinit var summaryView: TextView
    private lateinit var actionsContainer: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private var session: StaffSession? = null
    private var section: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_section)
        StaffUi.enableEdgeToEdge(this)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        section = intent.getStringExtra(EXTRA_SECTION).orEmpty()

        val appBar = findViewById<AppBarLayout>(R.id.staffAppBar)
        titleView = findViewById(R.id.staffToolbar)
        StaffUi.applyAppBarInsets(appBar)
        StaffUi.setupToolbar(
            toolbar = titleView,
            title = UiLabels.sectionTitle(section),
            showBack = true,
            showLogout = false,
            onBack = { finish() },
            onLogout = null,
        )
        summaryView = findViewById(R.id.adminSectionSummary)
        actionsContainer = findViewById(R.id.adminSectionActions)
        listContainer = findViewById(R.id.adminSectionList)
        statusView = findViewById(R.id.adminSectionStatus)

        addSectionShortcuts()
        loadSection()
    }

    private fun addSectionShortcuts() {
        actionsContainer.removeAllViews()
        when (section) {
            "schedule" -> addActionButton("Открыть календарь расписания") {
                openWorkTab(R.id.nav_schedule)
            }
            "clients" -> addActionButton("Открыть поиск клиентов") {
                openWorkTab(R.id.nav_clients)
            }
            "app_support" -> addActionButton("Открыть обращения с фильтрами") {
                openWorkTab(R.id.nav_support)
            }
        }
    }

    private fun addActionButton(label: String, action: () -> Unit) {
        actionsContainer.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
    }

    private fun openWorkTab(tab: Int) {
        startActivity(
            Intent(this, WorkActivity::class.java)
                .putExtra(WorkActivity.EXTRA_INITIAL_TAB, tab),
        )
    }

    private fun loadSection() {
        if (section.isBlank()) {
            summaryView.text = "Раздел не указан"
            return
        }
        statusView.visibility = View.VISIBLE
        statusView.text = "Загрузка..."
        thread {
            try {
                val cards = withRefresh { token ->
                    apiClient.loadSectionData(token, "admin", section)
                }
                val items = withRefresh { token -> apiClient.loadList(token, section) }
                runOnUiThread { renderSection(cards, items) }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.visibility = View.VISIBLE
                    statusView.text = UserFacingError.message(e)
                }
            }
        }
    }

    private fun renderSection(cards: SectionData, items: List<FeedListItem>) {
        summaryView.text = buildString {
            append("Показатели:\n")
            if (cards.cards.isEmpty()) {
                append("—\n")
            } else {
                cards.cards.forEach { (key, value) ->
                    append("${UiLabels.metricTitle(key)}: $value\n")
                }
            }
            append("\nЗаписей: ${items.size}")
        }
        listContainer.removeAllViews()
        if (items.isEmpty()) {
            addListBlock("Нет данных", "В этом разделе пока пусто", "")
        } else {
            items.forEach { item ->
                addClickableListBlock(item.title, item.subtitle, item.meta) {
                    openItem(item)
                }
            }
        }
        statusView.visibility = View.GONE
    }

    private fun openItem(item: FeedListItem) {
        when (item.refType) {
            "client" -> {
                val clientId = item.id ?: return
                startActivity(
                    Intent(this, ClientDetailActivity::class.java)
                        .putExtra(ClientDetailActivity.EXTRA_CLIENT_ID, clientId),
                )
            }
            "ticket" -> openWorkTab(R.id.nav_support)
            else -> {
                AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setMessage(buildString {
                        if (item.subtitle.isNotBlank()) append("${item.subtitle}\n\n")
                        append(item.meta)
                    })
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun addListBlock(title: String, subtitle: String, meta: String) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        block.addView(labelView(title, 16f, true))
        if (subtitle.isNotBlank()) block.addView(labelView(subtitle, 15f, false))
        if (meta.isNotBlank()) block.addView(labelView(meta, 13f, false, R.color.fc_text_secondary))
        listContainer.addView(block)
        listContainer.addView(divider())
    }

    private fun addClickableListBlock(
        title: String,
        subtitle: String,
        meta: String,
        onClick: () -> Unit,
    ) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        block.addView(labelView(title, 16f, true))
        if (subtitle.isNotBlank()) block.addView(labelView(subtitle, 15f, false))
        if (meta.isNotBlank()) block.addView(labelView(meta, 13f, false, R.color.fc_primary))
        listContainer.addView(block)
        listContainer.addView(divider())
    }

    private fun labelView(text: String, sp: Float, bold: Boolean, colorRes: Int = R.color.fc_text): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@AdminSectionActivity, colorRes))
        }
    }

    private fun divider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            )
            setBackgroundColor(ContextCompat.getColor(this@AdminSectionActivity, R.color.fc_text_secondary))
            alpha = 0.25f
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
