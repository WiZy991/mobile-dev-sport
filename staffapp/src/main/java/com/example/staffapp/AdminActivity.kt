package com.example.staffapp

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.concurrent.thread

class AdminActivity : AppCompatActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private lateinit var contentView: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private var session: StaffSession? = null
    private var config: RoleConfig? = null
    private var adminMenu: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        StaffUi.enableEdgeToEdge(this)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        val appBar = findViewById<AppBarLayout>(R.id.staffAppBar)
        val toolbar = findViewById<MaterialToolbar>(R.id.staffToolbar)
        StaffUi.applyAppBarInsets(appBar)
        StaffUi.applyBottomInsets(findViewById(R.id.bottomNavigation))
        StaffUi.setupToolbar(
            toolbar = toolbar,
            title = "Админка",
            showBack = true,
            showLogout = true,
            onBack = { finish() },
            onLogout = { logout() },
        )
        contentView = findViewById(R.id.adminContentView)
        listContainer = findViewById(R.id.adminListContainer)
        statusView = findViewById(R.id.adminStatusView)
        session = store.loadSession()
        config = store.loadConfig()

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val canOpenSupport = config?.adminSections?.contains("app_support") == true
            || config?.appSections?.contains("app_support") == true
        bottomNavigation.menu.findItem(R.id.nav_support)?.isVisible = canOpenSupport
        val canOpenClients = config?.adminSections?.contains("clients") == true
            || config?.appSections?.contains("clients") == true
        bottomNavigation.menu.findItem(R.id.nav_clients)?.isVisible = canOpenClients
        bottomNavigation.selectedItemId = R.id.nav_home
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, WorkActivity::class.java)
                            .putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_home),
                    )
                    true
                }
                R.id.nav_schedule -> {
                    startActivity(
                        Intent(this, WorkActivity::class.java)
                            .putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_schedule),
                    )
                    true
                }
                R.id.nav_profile -> {
                    startActivity(
                        Intent(this, WorkActivity::class.java)
                            .putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_profile),
                    )
                    true
                }
                R.id.nav_support -> {
                    startActivity(
                        Intent(this, WorkActivity::class.java)
                            .putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_support),
                    )
                    true
                }
                R.id.nav_clients -> {
                    startActivity(
                        Intent(this, WorkActivity::class.java)
                            .putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_clients),
                    )
                    true
                }
                else -> false
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

    private fun loadData() = runAsync("Загрузка админки...") {
        if (config?.adminSections.isNullOrEmpty()) {
            throw IllegalStateException("Нет доступа к админке")
        }
        val data = withRefresh { token -> apiClient.loadAdminData(token) }
        adminMenu = data.adminMenu
        runOnUiThread {
            contentView.text = buildString {
                append("Права: ${if (data.canWrite) "редактирование" else "только просмотр"}\n\n")
                append("Показатели:\n")
                data.widgets.forEach { (key, value) ->
                    append("${UiLabels.metricTitle(key)}: $value\n")
                }
                append("\nРазделы CRM — нажмите, чтобы открыть:")
            }
            renderSections(data.adminSections)
        }
        ""
    }

    private fun renderSections(sections: List<String>) {
        listContainer.removeAllViews()
        sections.forEach { section ->
            addSectionRow(
                title = adminMenu[section] ?: UiLabels.sectionTitle(section),
                subtitle = sectionHint(section),
            ) {
                openSection(section)
            }
        }
    }

    private fun sectionHint(section: String): String = when (section) {
        "dashboard" -> "Сводка, задачи, лиды, записи"
        "clients" -> "Список клиентов и карточки"
        "schedule" -> "Тренировки и календарь"
        "bookings" -> "Записи на занятия"
        "leads" -> "Воронка и новые лиды"
        "app_support" -> "Обращения из приложения"
        "finance" -> "Доходы и расходы"
        "crm_staff" -> "Сотрудники CRM"
        else -> "Данные раздела CRM"
    }

    private fun openSection(section: String) {
        startActivity(
            Intent(this, AdminSectionActivity::class.java)
                .putExtra(AdminSectionActivity.EXTRA_SECTION, section),
        )
    }

    private fun addSectionRow(title: String, subtitle: String, onClick: () -> Unit) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        block.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@AdminActivity, R.color.fc_text))
        })
        block.addView(TextView(this).apply {
            text = subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(this@AdminActivity, R.color.fc_primary))
        })
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            )
            setBackgroundColor(ContextCompat.getColor(this@AdminActivity, R.color.fc_text_secondary))
            alpha = 0.25f
        }
        listContainer.addView(block)
        listContainer.addView(divider)
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

    private fun runAsync(progress: String, action: () -> String) {
        statusView.visibility = View.VISIBLE
        statusView.text = progress
        thread {
            try {
                val result = action()
                runOnUiThread {
                    statusView.visibility = if (result.isBlank()) View.GONE else View.VISIBLE
                    if (result.isNotBlank()) statusView.text = result
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.visibility = View.VISIBLE
                    statusView.text = UserFacingError.message(e)
                }
            }
        }
    }
}
