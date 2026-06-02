package com.example.staffapp

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.concurrent.thread
import java.util.Calendar
import java.util.Locale

class WorkActivity : AppCompatActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore
    private lateinit var headerTitle: MaterialToolbar
    private lateinit var contentView: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var dayStrip: View
    private lateinit var daysContainer: LinearLayout
    private lateinit var clientsSearchBar: View
    private lateinit var clientsSearchInput: EditText
    private lateinit var clientsSearchButton: Button
    private lateinit var bottomNavigation: BottomNavigationView

    private var appData: StaffAppData? = null
    private var session: StaffSession? = null
    private var allowedSections: List<String> = emptyList()
    private var config: RoleConfig? = null
    private var requestedTab: Int = R.id.nav_home
    private var scheduleData: ScheduleData? = null
    private var selectedScheduleDate: String? = null
    private var selectedSupportFilter: String? = null
    private var clientsSearchQuery: String = ""
    private var activeTab: Int = R.id.nav_home
    private var loadGeneration = 0
    private var initialDataLoaded = false
    private val sessionLock = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work)
        StaffUi.enableEdgeToEdge(this)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        val appBar = findViewById<AppBarLayout>(R.id.staffAppBar)
        headerTitle = findViewById(R.id.staffToolbar)
        StaffUi.applyAppBarInsets(appBar)
        StaffUi.applyBottomInsets(findViewById(R.id.bottomNavigation))
        contentView = findViewById(R.id.workContentView)
        listContainer = findViewById(R.id.workListContainer)
        statusView = findViewById(R.id.workStatusView)
        dayStrip = findViewById(R.id.scheduleDayStrip)
        daysContainer = findViewById(R.id.scheduleDaysContainer)
        clientsSearchBar = findViewById(R.id.clientsSearchBar)
        clientsSearchInput = findViewById(R.id.clientsSearchInput)
        clientsSearchButton = findViewById(R.id.clientsSearchButton)
        session = store.loadSession()
        config = store.loadConfig()
        requestedTab = intent?.getIntExtra(EXTRA_INITIAL_TAB, R.id.nav_home) ?: R.id.nav_home

        StaffUi.setupToolbar(
            toolbar = headerTitle,
            title = "Главная",
            showBack = false,
            showLogout = true,
            onBack = null,
            onLogout = { logout() },
        )

        bottomNavigation = findViewById(R.id.bottomNavigation)
        updateBottomNavVisibility()
        clientsSearchButton.setOnClickListener {
            clientsSearchQuery = clientsSearchInput.text.toString().trim()
            loadClientsList(clientsSearchQuery)
        }

        activeTab = requestedTab
        bottomNavigation.selectedItemId = requestedTab
        bottomNavigation.setOnItemSelectedListener { item -> selectTab(item.itemId) }
        selectTab(requestedTab)

        StaffNotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        StaffPushRegistrar.registerIfLoggedIn(this)

        loadData()
    }

    private fun logout() {
        store.clearAll()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setScreenTitle(title: String) {
        headerTitle.title = title
    }

    private fun updateBottomNavVisibility() {
        config = store.loadConfig() ?: config
        val sections = config?.appSections.orEmpty()
        val adminSections = config?.adminSections.orEmpty()
        val canOpenSchedule = sections.contains("schedule") || adminSections.contains("schedule")
        bottomNavigation.menu.findItem(R.id.nav_schedule)?.isVisible = canOpenSchedule
        val canOpenSupport = sections.contains("app_support") || adminSections.contains("app_support")
        bottomNavigation.menu.findItem(R.id.nav_support)?.isVisible = canOpenSupport
        val canOpenClients = sections.contains("clients") || adminSections.contains("clients")
        bottomNavigation.menu.findItem(R.id.nav_clients)?.isVisible = canOpenClients
    }

    override fun onResume() {
        super.onResume()
        if (session != null && allowedSections.contains("app_support")) {
            pollUnreadNotifications()
        }
    }

    private fun loadData() = runAsync("Загрузка...") {
        val data = withRefresh { token -> apiClient.loadAppData(token) }
        appData = data
        allowedSections = data.sections
        initialDataLoaded = true
        StaffPushRegistrar.registerIfLoggedIn(this@WorkActivity)
        pollUnreadNotifications()
        runOnUiThread {
            updateBottomNavVisibility()
            refreshActiveTab()
        }
        ""
    }

    private fun refreshActiveTab() {
        bottomNavigation.selectedItemId = activeTab
        selectTab(activeTab)
    }

    private fun sectionAllowed(section: String): Boolean {
        if (allowedSections.contains(section)) return true
        val cfg = store.loadConfig() ?: config
        return cfg?.appSections?.contains(section) == true
            || cfg?.adminSections?.contains(section) == true
    }

    private fun pollUnreadNotifications() {
        if (!allowedSections.contains("app_support")) return
        thread {
            try {
                val notifications = withRefresh { token -> apiClient.loadStaffNotifications(token) }
                val previous = store.getLastUnreadNotificationCount()
                if (previous >= 0 && notifications.unreadCount > previous) {
                    val latest = notifications.items.firstOrNull { !it.isRead }
                    StaffNotificationHelper.showSupportNotification(
                        this,
                        latest?.title ?: "Новое обращение",
                        latest?.body ?: "Появилось новое обращение из приложения",
                    )
                }
                store.setLastUnreadNotificationCount(notifications.unreadCount)
            } catch (_: Exception) {
                // Фоновая проверка — не мешаем работе экрана.
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS,
        )
    }

    private fun showHomeTab() {
        setScreenTitle("Главная")
        dayStrip.visibility = View.GONE
        clientsSearchBar.visibility = View.GONE
        clearList()

        val data = appData
        if (data == null) {
            contentView.text = "Загрузка данных..."
            return
        }

        val role = primaryRole()
        contentView.text = buildString {
            append("Здравствуйте, ${data.employeeName}\n")
            append("${UiLabels.roleTitle(role)}\n\n")
            append("Сводка:\n")
            if (data.metrics.isEmpty()) {
                append("Нет показателей\n")
            } else {
                data.metrics.forEach { (key, value) ->
                    append("${UiLabels.metricTitle(key)}: $value\n")
                }
            }
        }

        if (!config?.adminSections.isNullOrEmpty() || allowedSections.contains("admin")) {
            addActionButton("Открыть админку") {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }

        val homeSection = when (role) {
            "ROLE_TRAINER" -> "bookings"
            "ROLE_MANAGER" -> "tasks"
            "ROLE_FINANCE" -> "subscriptions"
            "ROLE_SUPPORT" -> "app_support"
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN" -> "schedule"
            else -> allowedSections.firstOrNull { it in HOME_SECTIONS }
        }

        if (homeSection == "app_support" && sectionAllowed("app_support")) {
            runAsyncForTab(R.id.nav_home, "Загрузка...") {
                val tickets = withRefresh { token -> apiClient.loadSupportTickets(token) }
                if (activeTab != R.id.nav_home) return@runAsyncForTab ""
                runOnUiThread {
                    appendSectionTitle("Новые обращения: ${tickets.newCount}")
                    tickets.items.filter { it.status == "new" }.take(5).forEach { renderSupportTicket(it, false) }
                    if (tickets.items.none { it.status == "new" }) {
                        addListBlock("Новых обращений нет", "", "")
                    }
                }
                ""
            }
        } else if (homeSection != null && sectionAllowed(homeSection)) {
            runAsyncForTab(R.id.nav_home, "Загрузка...") {
                if (role == "ROLE_TRAINER" && sectionAllowed("schedule")) {
                    val schedule = loadScheduleCached()
                    if (activeTab != R.id.nav_home) return@runAsyncForTab ""
                    runOnUiThread {
                        appendSectionTitle("Ваши тренировки сегодня")
                        renderScheduleItems(schedule.items.filter { it.date == todayDate() }.ifEmpty {
                            schedule.items.take(5)
                        })
                    }
                } else {
                    val items = withRefresh { token -> apiClient.loadList(token, homeSection) }
                    if (activeTab != R.id.nav_home) return@runAsyncForTab ""
                    runOnUiThread {
                        appendSectionTitle(UiLabels.sectionTitle(homeSection))
                        renderFeedItems(items.take(8))
                    }
                }
                ""
            }
        } else if (sectionAllowed("schedule")) {
            runAsyncForTab(R.id.nav_home, "Загрузка...") {
                val schedule = loadScheduleCached(forceRefresh = false)
                if (activeTab != R.id.nav_home) return@runAsyncForTab ""
                runOnUiThread {
                    appendSectionTitle("Ближайшие тренировки")
                    renderScheduleItems(schedule.items.take(5))
                }
                ""
            }
        }
    }

    private fun showScheduleTab() {
        setScreenTitle("Расписание")
        clientsSearchBar.visibility = View.GONE
        contentView.text = "Загрузка расписания..."
        clearList()
        dayStrip.visibility = View.GONE
        if (!sectionAllowed("schedule")) {
            contentView.text = if (initialDataLoaded) {
                "Раздел «Расписание» недоступен для вашей должности."
            } else {
                "Загрузка данных..."
            }
            return
        }

        runAsyncForTab(R.id.nav_schedule, "Загрузка расписания...") {
            val schedule = loadScheduleCached(forceRefresh = true)
            if (selectedScheduleDate == null) {
                selectedScheduleDate = schedule.days.firstOrNull { it.date == todayDate() }?.date
                    ?: schedule.days.firstOrNull()?.date
            }
            scheduleData = schedule
            if (activeTab != R.id.nav_schedule) return@runAsyncForTab ""
            runOnUiThread { renderScheduleCalendar(schedule) }
            ""
        }
    }

    private fun renderScheduleCalendar(schedule: ScheduleData) {
        dayStrip.visibility = View.VISIBLE
        daysContainer.removeAllViews()
        clearList()

        schedule.days.forEach { day ->
            val chip = TextView(this).apply {
                text = "${day.label}\n${day.count}"
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                val selected = day.date == selectedScheduleDate
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    ContextCompat.getColor(
                        this@WorkActivity,
                        if (selected) android.R.color.white else R.color.fc_text
                    )
                )
                setBackgroundColor(
                    ContextCompat.getColor(
                        this@WorkActivity,
                        if (selected) R.color.fc_primary else R.color.fc_background
                    )
                )
                setOnClickListener {
                    selectedScheduleDate = day.date
                    renderScheduleCalendar(schedule)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(8)
            }
            daysContainer.addView(chip, params)
        }

        val selectedDate = selectedScheduleDate
        val dayItems = schedule.items.filter { it.date == selectedDate }
        val dayLabel = schedule.days.firstOrNull { it.date == selectedDate }?.label ?: selectedDate.orEmpty()

        contentView.text = if (dayItems.isEmpty()) {
            "$dayLabel\n\nНа этот день записей нет."
        } else {
            "$dayLabel\n\n${dayItems.size} занятий"
        }
        renderScheduleItems(dayItems)
    }

    private fun renderScheduleItems(items: List<ScheduleItem>) {
        clearList()
        items.forEach { item ->
            val clients = if (item.clientNames.isNotEmpty()) {
                item.clientNames.joinToString(", ")
            } else {
                item.participants.ifBlank { "нет записей" }
            }
            addListBlock(
                title = "${item.startTime}–${item.endTime}  ${item.title}",
                subtitle = "Клиенты: $clients",
                meta = "${UiLabels.trainingType(item.type)} · ${item.trainer} · ${item.room}",
            )
        }
    }

    private fun showSupportTab() {
        setScreenTitle("Обращения")
        clientsSearchBar.visibility = View.GONE
        contentView.text = "Загрузка обращений..."
        clearList()
        if (!sectionAllowed("app_support")) {
            dayStrip.visibility = View.GONE
            contentView.text = if (initialDataLoaded) {
                "Раздел «Обращения» недоступен для вашей должности."
            } else {
                "Загрузка данных..."
            }
            return
        }

        renderSupportFilterStrip()

        runAsyncForTab(R.id.nav_support, "Загрузка обращений...") {
            val tickets = withRefresh { token ->
                apiClient.loadSupportTickets(token, selectedSupportFilter)
            }
            val notifications = withRefresh { token -> apiClient.loadStaffNotifications(token) }
            store.setLastUnreadNotificationCount(notifications.unreadCount)
            if (activeTab != R.id.nav_support) return@runAsyncForTab ""
            runOnUiThread {
                contentView.text = buildString {
                    append("Новых обращений: ${tickets.newCount}\n")
                    if (notifications.unreadCount > 0) {
                        append("Непрочитанных уведомлений: ${notifications.unreadCount}\n")
                    }
                    append("\nСписок обращений из клиентского приложения:")
                }
                notifications.items.filter { !it.isRead }.take(5).forEach { n ->
                    addListBlock(n.title, n.body, n.createdAt)
                }
                if (notifications.unreadCount > 0 && canWriteSupport()) {
                    addActionButton("Отметить все уведомления прочитанными") {
                        runAsyncForTab(R.id.nav_support, "Сохранение...") {
                            withRefresh { token -> apiClient.markAllStaffNotificationsRead(token) }
                            showSupportTab()
                            ""
                        }
                    }
                }
                if (tickets.items.isEmpty()) {
                    addListBlock("Обращений по фильтру нет", "", "")
                } else {
                    tickets.items.forEach { renderSupportTicket(it, canWriteSupport()) }
                }
            }
            ""
        }
    }

    private fun renderSupportFilterStrip() {
        dayStrip.visibility = View.VISIBLE
        daysContainer.removeAllViews()
        val filters = listOf(
            null to "Все",
            "new" to "Новые",
            "in_progress" to "В работе",
            "done" to "Закрыто",
        )
        filters.forEach { (value, label) ->
            val chip = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                val selected = selectedSupportFilter == value
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(
                    ContextCompat.getColor(
                        this@WorkActivity,
                        if (selected) android.R.color.white else R.color.fc_text,
                    )
                )
                setBackgroundColor(
                    ContextCompat.getColor(
                        this@WorkActivity,
                        if (selected) R.color.fc_primary else R.color.fc_background,
                    )
                )
                setOnClickListener {
                    selectedSupportFilter = value
                    showSupportTab()
                }
            }
            daysContainer.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(8) },
            )
        }
    }

    private fun showClientsTab() {
        setScreenTitle("Клиенты")
        dayStrip.visibility = View.GONE
        clearList()
        if (!sectionAllowed("clients")) {
            clientsSearchBar.visibility = View.GONE
            contentView.text = if (initialDataLoaded) {
                "Раздел «Клиенты» недоступен для вашей должности."
            } else {
                "Загрузка данных..."
            }
            return
        }

        clientsSearchBar.visibility = View.VISIBLE
        clientsSearchInput.setText(clientsSearchQuery)
        contentView.text = "Найдено клиентов: загрузка..."
        loadClientsList(clientsSearchQuery)
    }

    private fun loadClientsList(query: String) {
        runAsyncForTab(R.id.nav_clients, "Загрузка клиентов...") {
            val clients = withRefresh { token -> apiClient.loadClients(token, query) }
            if (activeTab != R.id.nav_clients) return@runAsyncForTab ""
            runOnUiThread {
                contentView.text = if (clients.isEmpty()) {
                    "Клиенты не найдены"
                } else {
                    "Клиентов: ${clients.size}"
                }
                clearList()
                clients.forEach { client ->
                    val subtitle = buildString {
                        if (client.email.isNotBlank()) append(client.email)
                        if (client.phone.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
                            append(client.phone)
                        }
                    }
                    addClickableListBlock(
                        title = client.name.ifBlank { "Клиент #${client.id}" },
                        subtitle = subtitle,
                        meta = "Открыть карточку",
                    ) {
                        openClientCard(client.id)
                    }
                }
            }
            ""
        }
    }

    private fun openClientCard(clientId: Int) {
        startActivity(
            Intent(this, ClientDetailActivity::class.java)
                .putExtra(ClientDetailActivity.EXTRA_CLIENT_ID, clientId),
        )
    }

    private fun renderSupportTicket(ticket: SupportTicketItem, allowStatusChange: Boolean) {
        val client = ticket.clientName.ifBlank { ticket.contactEmail.ifBlank { "Клиент не указан" } }
        val contact = buildString {
            if (ticket.contactEmail.isNotBlank()) append(ticket.contactEmail)
            if (ticket.clientPhone.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(ticket.clientPhone)
            }
        }
        addListBlock(
            title = "${UiLabels.ticketStatus(ticket.status)} · ${ticket.subject}",
            subtitle = "Клиент: $client\n${ticket.message}",
            meta = "${UiLabels.ticketCategory(ticket.category)} · ${ticket.createdAt}${if (contact.isNotBlank()) "\n$contact" else ""}",
        )
        if (ticket.clientId != null && sectionAllowed("clients")) {
            addActionButton("Карточка клиента") { openClientCard(ticket.clientId) }
        }
        if (allowStatusChange && ticket.status != "done") {
            if (ticket.status == "new") {
                addActionButton("Взять в работу") { updateTicketStatus(ticket.id, "in_progress") }
            }
            if (ticket.status != "done") {
                addActionButton("Закрыть обращение") { updateTicketStatus(ticket.id, "done") }
            }
        }
    }

    private fun addActionButton(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }
        listContainer.addView(button)
    }

    private fun updateTicketStatus(ticketId: Int, status: String) {
        runAsyncForTab(R.id.nav_support, "Обновление статуса...") {
            withRefresh { token -> apiClient.updateSupportTicketStatus(token, ticketId, status) }
            showSupportTab()
            ""
        }
    }

    private fun canWriteSupport(): Boolean =
        config?.adminActions?.contains("admin.write") == true
            || config?.adminActions?.contains("support.write") == true

    private fun showProfileTab() {
        setScreenTitle("Профиль")
        dayStrip.visibility = View.GONE
        clientsSearchBar.visibility = View.GONE
        clearList()
        config = store.loadConfig() ?: config

        val data = appData
        val sections = if (allowedSections.isNotEmpty()) {
            allowedSections
        } else {
            config?.appSections.orEmpty()
        }
        contentView.text = if (data == null) {
            buildString {
                append("${session?.userEmail.orEmpty()}\n")
                append("${UiLabels.roleTitle(primaryRole())}\n\n")
                append("Загрузка профиля...")
            }
        } else {
            buildString {
                append("${data.employeeName}\n")
                append("${data.employeeEmail}\n")
                append("${UiLabels.roleTitle(primaryRole())}\n\n")
                append("Доступные разделы:\n")
                sections
                    .filterNot { it == "home" || it == "profile" || it == "admin" }
                    .forEach { append("• ${UiLabels.sectionTitle(it)}\n") }
                append("\n")
                append(
                    if (!config?.adminSections.isNullOrEmpty() || sections.contains("admin")) {
                        "Админка CRM доступна."
                    } else {
                        "Админка для вашей должности недоступна."
                    }
                )
            }
        }

        if (!config?.adminSections.isNullOrEmpty() || sections.contains("admin")) {
            addActionButton("Открыть админку") {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }

        val extraSections = sections.filter {
            it !in setOf("home", "profile", "schedule", "dashboard", "admin", "clients", "app_support")
        }
        if (extraSections.isNotEmpty()) {
            runAsyncForTab(R.id.nav_profile, "Загрузка...") {
                val section = extraSections.first()
                val items = withRefresh { token -> apiClient.loadList(token, section) }
                if (activeTab != R.id.nav_profile) return@runAsyncForTab ""
                runOnUiThread {
                    appendSectionTitle(UiLabels.sectionTitle(section))
                    renderFeedItems(items.take(10))
                }
                ""
            }
        }
    }

    private fun appendSectionTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text))
            setPadding(0, dp(12), 0, dp(4))
        }
        listContainer.addView(titleView)
    }

    private fun renderFeedItems(items: List<FeedListItem>) {
        if (items.isEmpty()) {
            addListBlock("Нет данных", "", "")
            return
        }
        items.forEach { item ->
            addListBlock(item.title, item.subtitle, item.meta)
        }
    }

    private fun addListBlock(title: String, subtitle: String, meta: String) {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        block.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text))
        })
        if (subtitle.isNotBlank()) {
            block.addView(TextView(this).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text))
            })
        }
        if (meta.isNotBlank()) {
            block.addView(TextView(this).apply {
                text = meta
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text_secondary))
            })
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            )
            setBackgroundColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text_secondary))
            alpha = 0.25f
        }
        listContainer.addView(block)
        listContainer.addView(divider)
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
        block.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text))
        })
        if (subtitle.isNotBlank()) {
            block.addView(TextView(this).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text))
            })
        }
        if (meta.isNotBlank()) {
            block.addView(TextView(this).apply {
                text = meta
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_primary))
            })
        }
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            )
            setBackgroundColor(ContextCompat.getColor(this@WorkActivity, R.color.fc_text_secondary))
            alpha = 0.25f
        }
        listContainer.addView(block)
        listContainer.addView(divider)
    }

    private fun clearList() {
        listContainer.removeAllViews()
    }

    private fun selectTab(itemId: Int): Boolean {
        activeTab = itemId
        statusView.visibility = View.GONE
        if (itemId != R.id.nav_clients) {
            clientsSearchBar.visibility = View.GONE
        }
        return when (itemId) {
            R.id.nav_home -> {
                showHomeTab()
                true
            }
            R.id.nav_schedule -> {
                showScheduleTab()
                true
            }
            R.id.nav_profile -> {
                showProfileTab()
                true
            }
            R.id.nav_support -> {
                showSupportTab()
                true
            }
            R.id.nav_clients -> {
                showClientsTab()
                true
            }
            else -> false
        }
    }

    private fun primaryRole(): String {
        val roles = config?.roles.orEmpty()
        val priority = listOf(
            "ROLE_SUPER_ADMIN",
            "ROLE_ADMIN",
            "ROLE_TRAINER",
            "ROLE_MANAGER",
            "ROLE_SUPPORT",
            "ROLE_FINANCE",
            "ROLE_VIEWER",
        )
        return priority.firstOrNull { roles.contains(it) } ?: roles.firstOrNull() ?: "ROLE_VIEWER"
    }

    private fun todayDate(): String {
        val cal = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun loadScheduleCached(forceRefresh: Boolean = false): ScheduleData {
        if (!forceRefresh) {
            scheduleData?.let { return it }
        }
        return withRefresh { token -> apiClient.loadSchedule(token) }.also { scheduleData = it }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun <T> withRefresh(action: (String) -> T): T {
        synchronized(sessionLock) {
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

    private fun runAsyncForTab(tab: Int, progress: String, action: () -> String) {
        val generation = ++loadGeneration
        statusView.visibility = View.VISIBLE
        statusView.text = progress
        thread {
            try {
                val result = action()
                runOnUiThread {
                    if (generation != loadGeneration || activeTab != tab) return@runOnUiThread
                    if (result.isBlank()) {
                        statusView.visibility = View.GONE
                    } else {
                        statusView.text = result
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation != loadGeneration || activeTab != tab) return@runOnUiThread
                    statusView.visibility = View.VISIBLE
                    statusView.text = UserFacingError.message(e)
                    if (tab == R.id.nav_schedule) {
                        scheduleData = null
                    }
                    addActionButton("Повторить") {
                        selectTab(tab)
                    }
                }
            }
        }
    }

    private fun runAsync(progress: String, action: () -> String) {
        runAsyncForTab(activeTab, progress, action)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private const val REQUEST_NOTIFICATIONS = 42
        private val HOME_SECTIONS = setOf("bookings", "clients", "tasks", "subscriptions", "schedule", "app_support")
    }
}
