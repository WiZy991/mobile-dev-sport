package com.example.staffapp

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
import com.example.staffapp.ui.theme.StaffTheme
import com.example.staffapp.ui.work.ActionUi
import com.example.staffapp.ui.work.AssignClientDialogUi
import com.example.staffapp.ui.work.CreateSessionDialogUi
import com.example.staffapp.ui.work.ProfileSectionUi
import com.example.staffapp.ui.work.SectionHints
import com.example.staffapp.ui.work.BadgeColor
import com.example.staffapp.ui.work.ClientsTabUi
import com.example.staffapp.ui.work.DayChipUi
import com.example.staffapp.ui.work.HomeTabUi
import com.example.staffapp.ui.work.ListCardUi
import com.example.staffapp.ui.work.MetricUi
import com.example.staffapp.ui.work.ProfileTabUi
import com.example.staffapp.ui.work.ScheduleBookingUi
import com.example.staffapp.ui.work.ScheduleDayUi
import com.example.staffapp.ui.work.ScheduleSessionUi
import com.example.staffapp.ui.work.ScheduleTabUi
import com.example.staffapp.ui.work.SupportTabUi
import com.example.staffapp.ui.work.WorkScreen
import com.example.staffapp.ui.work.WorkUiState
import kotlin.concurrent.thread
import java.util.Calendar
import java.util.Locale

class WorkActivity : ComponentActivity() {
    private lateinit var apiClient: StaffApiClient
    private lateinit var store: StaffSessionStore

    private var appData: StaffAppData? = null
    private var session: StaffSession? = null
    private var allowedSections: List<String> = emptyList()
    private var config: RoleConfig? = null
    private var scheduleData: ScheduleData? = null
    private var selectedScheduleDate: String? = null
    private var selectedScheduleTypeFilter: String? = null
    private var selectedSupportFilter: String? = null
    private var clientsSearchQuery: String = ""
    private var loadGeneration = 0
    private var initialDataLoaded = false
    private val sessionLock = Any()

    private var uiState by mutableStateOf(WorkUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiClient = StaffApiClient(StaffApiUrl.resolve(this))
        store = StaffSessionStore(this)
        session = store.loadSession()
        config = store.loadConfig()

        val requestedTab = tabFromNavId(
            intent?.getIntExtra(EXTRA_INITIAL_TAB, R.id.nav_home) ?: R.id.nav_home,
        )

        uiState = uiState.copy(selectedTab = requestedTab, screenTitle = tabTitle(requestedTab))
        updateNavVisibility()

        setContent {
            StaffTheme {
                WorkScreen(
                    state = uiState,
                    onTabSelected = { selectTab(it) },
                    onLogout = { logout() },
                    onAction = { handleAction(it) },
                    onScheduleDaySelected = { date ->
                        selectedScheduleDate = date
                        scheduleData?.let { renderSchedule(it) }
                    },
                    onScheduleTypeFilterSelected = { filter ->
                        selectedScheduleTypeFilter = filter
                        scheduleData?.let { renderSchedule(it) }
                    },
                    onSupportFilterSelected = { filter ->
                        selectedSupportFilter = filter.ifBlank { null }
                        showSupportTab()
                    },
                    onClientSearchQueryChange = { query ->
                        clientsSearchQuery = query
                        uiState = uiState.copy(clients = uiState.clients.copy(query = query))
                    },
                    onClientSearch = {
                        clientsSearchQuery = uiState.clients.query
                        loadClientsList(clientsSearchQuery)
                    },
                    onListCardClick = { handleListCardClick(it) },
                    onProfileSectionClick = { handleProfileSectionClick(it) },
                    onScheduleSessionClick = { openAssignDialog(it) },
                    onAssignQueryChange = { q ->
                        uiState.assignDialog?.let { d ->
                            uiState = uiState.copy(assignDialog = d.copy(query = q))
                        }
                    },
                    onAssignSearch = { searchAssignClients() },
                    onAssignBook = { bookAssignClient(it) },
                    onAssignCancelBooking = { cancelAssignBooking(it) },
                    onAssignDismiss = { uiState = uiState.copy(assignDialog = null) },
                    onCreateSessionClick = { openCreateSessionDialog() },
                    onCreateNameChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(name = v))
                        }
                    },
                    onCreateTypeChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(
                                createSessionDialog = it.copy(
                                    type = v,
                                    maxParticipants = if (v == "personal") "1" else it.maxParticipants.ifBlank { "10" },
                                    name = when (v) {
                                        "group" -> if (it.name.contains("Персональ", true)) "Групповое занятие" else it.name
                                        else -> if (it.name.contains("Группов", true)) "Персональная тренировка" else it.name
                                    },
                                ),
                            )
                        }
                    },
                    onCreateStartTimeChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(startTime = v))
                        }
                    },
                    onCreateEndTimeChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(endTime = v))
                        }
                    },
                    onCreateRoomChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(room = v))
                        }
                    },
                    onCreateMaxParticipantsChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(maxParticipants = v))
                        }
                    },
                    onCreateConfirm = { createSession() },
                    onCreateDismiss = { uiState = uiState.copy(createSessionDialog = null) },
                )
            }
        }

        StaffNotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        StaffPushRegistrar.registerIfLoggedIn(this)
        thread {
            try {
                val onboarding = withRefresh { apiClient.loadOnboarding(it) }
                if (onboarding.status != "active") {
                    runOnUiThread {
                        startActivity(Intent(this, OnboardingActivity::class.java))
                        finish()
                    }
                    return@thread
                }
                runOnUiThread {
                    selectTab(requestedTab)
                    loadData()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    selectTab(requestedTab)
                    loadData()
                }
            }
        }
    }

    private fun handleListCardClick(card: ListCardUi) {
        card.clientId?.let {
            openClientCard(it)
            return
        }
        when (card.refType) {
            "client" -> card.feedId?.let { openClientCard(it) }
            "ticket" -> selectTab(WorkUiState.TAB_SUPPORT)
        }
    }

    private fun handleProfileSectionClick(sectionKey: String) {
        when (sectionKey) {
            "schedule" -> if (uiState.showScheduleNav) selectTab(WorkUiState.TAB_SCHEDULE)
            "clients" -> if (uiState.showClientsNav) selectTab(WorkUiState.TAB_CLIENTS)
            "app_support" -> if (uiState.showSupportNav) selectTab(WorkUiState.TAB_SUPPORT)
            else -> {
                val adminSections = config?.adminSections.orEmpty()
                if (adminSections.contains(sectionKey)) {
                    startActivity(
                        Intent(this, AdminSectionActivity::class.java)
                            .putExtra(AdminSectionActivity.EXTRA_SECTION, sectionKey),
                    )
                }
            }
        }
    }

    private fun handleAction(actionId: String) {
        when {
            actionId == "open_admin" -> startActivity(Intent(this, AdminActivity::class.java))
            actionId == "edit_trainer_profile" -> startActivity(Intent(this, TrainerProfileActivity::class.java))
            actionId == "retry" -> selectTab(uiState.selectedTab)
            actionId == "mark_notifications_read" -> {
                runAsyncForTab(uiState.selectedTab, "Сохранение...") {
                    withRefresh { token -> apiClient.markAllStaffNotificationsRead(token) }
                    showSupportTab()
                    ""
                }
            }
            actionId.startsWith("ticket_status:") -> {
                val parts = actionId.split(":")
                if (parts.size == 3) {
                    updateTicketStatus(parts[1].toInt(), parts[2])
                }
            }
            actionId.startsWith("ticket_client:") -> {
                openClientCard(actionId.removePrefix("ticket_client:").toInt())
            }
        }
    }

    private fun logout() {
        store.clearAll()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun updateNavVisibility() {
        config = store.loadConfig() ?: config
        val sections = (config?.appSections.orEmpty() + allowedSections).distinct()
        val adminSections = config?.adminSections.orEmpty()
        uiState = uiState.copy(
            showScheduleNav = sections.contains("schedule") || adminSections.contains("schedule"),
            showClientsNav = sections.contains("clients") || adminSections.contains("clients"),
            showSupportNav = sections.contains("app_support") || adminSections.contains("app_support"),
        )
    }

    override fun onResume() {
        super.onResume()
        if (session != null && allowedSections.contains("app_support")) {
            pollUnreadNotifications()
        }
    }

    private fun loadData() = runAsync("Загрузка...") {
        val cfg = withRefresh { token -> apiClient.loadConfig(token) }
        config = cfg
        store.saveConfig(cfg)
        val data = withRefresh { token -> apiClient.loadAppData(token) }
        appData = data
        allowedSections = data.sections
        initialDataLoaded = true
        StaffPushRegistrar.registerIfLoggedIn(this@WorkActivity)
        pollUnreadNotifications()
        runOnUiThread {
            updateNavVisibility()
            refreshActiveTab()
        }
        ""
    }

    private fun refreshActiveTab() = selectTab(uiState.selectedTab)

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
        uiState = uiState.copy(
            screenTitle = "Главная",
            errorMessage = null,
            home = HomeTabUi(loading = appData == null),
        )
        val data = appData ?: return
        val role = primaryRole()
        val showAdmin = config?.adminActions?.contains("admin.write") == true
            || role == "ROLE_ADMIN"
            || role == "ROLE_SUPER_ADMIN"
            || role == "ROLE_MANAGER"
        val metrics = data.metrics.map { (key, value) ->
            MetricUi(UiLabels.metricTitle(key), value.toString())
        }
        uiState = uiState.copy(
            home = HomeTabUi(
                greeting = "Здравствуйте, ${data.employeeName}",
                roleTitle = UiLabels.roleTitle(role),
                metrics = metrics,
                showAdminButton = showAdmin,
                loading = true,
            ),
        )

        val homeSection = when (role) {
            "ROLE_TRAINER" -> "bookings"
            "ROLE_MANAGER" -> "tasks"
            "ROLE_FINANCE" -> "subscriptions"
            "ROLE_SUPPORT" -> "app_support"
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN" -> "schedule"
            else -> allowedSections.firstOrNull { it in HOME_SECTIONS }
        }

        if (homeSection == "app_support" && sectionAllowed("app_support")) {
            runAsyncForTab(WorkUiState.TAB_HOME, "Загрузка...") {
                val tickets = withRefresh { token -> apiClient.loadSupportTickets(token) }
                if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runAsyncForTab ""
                val items = tickets.items.filter { it.status == "new" }.take(5).map { ticketToCard(it) }
                runOnUiThread {
                    uiState = uiState.copy(
                        home = uiState.home.copy(
                            sectionTitle = "Новые обращения: ${tickets.newCount}",
                            items = items,
                            emptyMessage = if (items.isEmpty()) "Новых обращений нет" else null,
                            loading = false,
                        ),
                    )
                }
                ""
            }
        } else if (homeSection != null && sectionAllowed(homeSection)) {
            runAsyncForTab(WorkUiState.TAB_HOME, "Загрузка...") {
                if (role == "ROLE_TRAINER" && sectionAllowed("schedule")) {
                    val schedule = loadScheduleCached()
                    if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runAsyncForTab ""
                    val items = schedule.items.filter { it.date == todayDate() }.ifEmpty {
                        schedule.items.take(5)
                    }.map { scheduleToCard(it) }
                    runOnUiThread {
                        uiState = uiState.copy(
                            home = uiState.home.copy(
                                sectionTitle = "Ваши тренировки сегодня",
                                items = items,
                                emptyMessage = if (items.isEmpty()) "Нет тренировок" else null,
                                loading = false,
                            ),
                        )
                    }
                } else {
                    val items = withRefresh { token -> apiClient.loadList(token, homeSection) }
                    if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runAsyncForTab ""
                    runOnUiThread {
                        uiState = uiState.copy(
                            home = uiState.home.copy(
                                sectionTitle = UiLabels.sectionTitle(homeSection),
                                items = items.take(8).map { feedToCard(it) },
                                emptyMessage = if (items.isEmpty()) "Нет данных" else null,
                                loading = false,
                            ),
                        )
                    }
                }
                ""
            }
        } else if (sectionAllowed("schedule")) {
            runAsyncForTab(WorkUiState.TAB_HOME, "Загрузка...") {
                val schedule = loadScheduleCached(forceRefresh = false)
                if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runAsyncForTab ""
                val items = schedule.items.take(5).map { scheduleToCard(it) }
                runOnUiThread {
                    uiState = uiState.copy(
                        home = uiState.home.copy(
                            sectionTitle = "Ближайшие тренировки",
                            items = items,
                            emptyMessage = if (items.isEmpty()) "Нет тренировок" else null,
                            loading = false,
                        ),
                    )
                }
                ""
            }
        } else {
            uiState = uiState.copy(home = uiState.home.copy(loading = false))
        }
    }

    private fun showScheduleTab() {
        uiState = uiState.copy(
            screenTitle = "Расписание",
            schedule = ScheduleTabUi(loading = true),
            errorMessage = null,
        )
        if (!sectionAllowed("schedule")) {
            uiState = uiState.copy(
                schedule = ScheduleTabUi(
                    denied = true,
                    deniedMessage = if (initialDataLoaded) {
                        "Раздел «Расписание» недоступен для вашей должности."
                    } else {
                        "Загрузка данных..."
                    },
                    loading = false,
                ),
            )
            return
        }
        runAsyncForTab(WorkUiState.TAB_SCHEDULE, "Загрузка расписания...") {
            val schedule = loadScheduleCached(forceRefresh = true)
            if (selectedScheduleDate == null) {
                selectedScheduleDate = schedule.days.firstOrNull { it.date == todayDate() }?.date
                    ?: schedule.days.firstOrNull()?.date
            }
            scheduleData = schedule
            if (uiState.selectedTab != WorkUiState.TAB_SCHEDULE) return@runAsyncForTab ""
            runOnUiThread { renderSchedule(schedule) }
            ""
        }
    }

    private fun renderSchedule(schedule: ScheduleData) {
        val today = todayDate()
        val typeFilter = selectedScheduleTypeFilter
        val days = schedule.days.map { day ->
            val (weekday, dayNumber) = parseDayLabel(day.label)
            ScheduleDayUi(
                date = day.date,
                weekdayLabel = weekday,
                dayNumber = dayNumber,
                sessionCount = day.count,
                selected = day.date == selectedScheduleDate,
                isToday = day.date == today,
            )
        }
        val selectedDate = selectedScheduleDate
        val dayItems = schedule.items
            .filter { it.date == selectedDate }
            .filter { typeFilter == null || it.type == typeFilter }
        uiState = uiState.copy(
            schedule = ScheduleTabUi(
                days = days,
                sessions = dayItems.map { scheduleToSession(it) },
                selectedTypeFilter = typeFilter,
                loading = false,
            ),
        )
    }

    private fun showSupportTab() {
        uiState = uiState.copy(
            screenTitle = "Обращения",
            support = SupportTabUi(loading = true, filters = supportFilters()),
            errorMessage = null,
        )
        if (!sectionAllowed("app_support")) {
            uiState = uiState.copy(
                support = SupportTabUi(
                    denied = true,
                    deniedMessage = if (initialDataLoaded) {
                        "Раздел «Обращения» недоступен для вашей должности."
                    } else {
                        "Загрузка данных..."
                    },
                    loading = false,
                ),
            )
            return
        }
        runAsyncForTab(WorkUiState.TAB_SUPPORT, "Загрузка обращений...") {
            val tickets = withRefresh { token ->
                apiClient.loadSupportTickets(token, selectedSupportFilter)
            }
            val notifications = withRefresh { token -> apiClient.loadStaffNotifications(token) }
            store.setLastUnreadNotificationCount(notifications.unreadCount)
            if (uiState.selectedTab != WorkUiState.TAB_SUPPORT) return@runAsyncForTab ""
            val allowWrite = canWriteSupport()
            val actions = buildList<ActionUi> {
                if (notifications.unreadCount > 0 && allowWrite) {
                    add(ActionUi("mark_notifications_read", "Отметить все уведомления прочитанными"))
                }
            }
            val ticketActions = tickets.items.associate { ticket ->
                ticket.id to buildTicketActions(ticket, allowWrite)
            }
            runOnUiThread {
                uiState = uiState.copy(
                    support = SupportTabUi(
                        newCount = tickets.newCount,
                        unreadCount = notifications.unreadCount,
                        filters = supportFilters(),
                        notifications = notifications.items.filter { !it.isRead }.take(5).map {
                            ListCardUi(title = it.title, subtitle = it.body, meta = it.createdAt)
                        },
                        tickets = tickets.items.map { ticketToCard(it) },
                        ticketActions = ticketActions,
                        actions = actions,
                        loading = false,
                    ),
                )
            }
            ""
        }
    }

    private fun supportFilters(): List<DayChipUi> = listOf(
        DayChipUi("", "Все", -1, selectedSupportFilter == null),
        DayChipUi("new", "Новые", -1, selectedSupportFilter == "new"),
        DayChipUi("in_progress", "В работе", -1, selectedSupportFilter == "in_progress"),
        DayChipUi("done", "Закрыто", -1, selectedSupportFilter == "done"),
    )

    private fun showClientsTab() {
        uiState = uiState.copy(
            screenTitle = "Клиенты",
            clients = ClientsTabUi(query = clientsSearchQuery, loading = true),
            errorMessage = null,
        )
        if (!sectionAllowed("clients")) {
            uiState = uiState.copy(
                clients = ClientsTabUi(
                    denied = true,
                    deniedMessage = if (initialDataLoaded) {
                        "Раздел «Клиенты» недоступен для вашей должности."
                    } else {
                        "Загрузка данных..."
                    },
                    loading = false,
                ),
            )
            return
        }
        loadClientsList(clientsSearchQuery)
    }

    private fun loadClientsList(query: String) {
        uiState = uiState.copy(
            clients = uiState.clients.copy(query = query, loading = true, summary = ""),
        )
        runAsyncForTab(WorkUiState.TAB_CLIENTS, "Загрузка клиентов...") {
            val clients = withRefresh { token -> apiClient.loadClients(token, query) }
            if (uiState.selectedTab != WorkUiState.TAB_CLIENTS) return@runAsyncForTab ""
            runOnUiThread {
                uiState = uiState.copy(
                    clients = ClientsTabUi(
                        query = query,
                        summary = if (clients.isEmpty()) "" else "Найдено: ${clients.size}",
                        items = clients.map { client ->
                            ListCardUi(
                                title = client.name.ifBlank { "Клиент #${client.id}" },
                                subtitle = listOf(client.email, client.phone).filter { it.isNotBlank() }.joinToString("\n"),
                                meta = "Открыть карточку",
                                clientId = client.id,
                            )
                        },
                        loading = false,
                    ),
                )
            }
            ""
        }
    }

    private fun showProfileTab() {
        config = store.loadConfig() ?: config
        val data = appData
        val sections = if (allowedSections.isNotEmpty()) allowedSections else config?.appSections.orEmpty()
        val adminAvailable = config?.adminActions?.contains("admin.write") == true
            || primaryRole() in setOf("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_MANAGER")
        val showTrainerEdit = primaryRole() == "ROLE_TRAINER"
            || (config?.roles.orEmpty() + appData?.roles.orEmpty()).contains("ROLE_TRAINER")
        uiState = uiState.copy(
            screenTitle = "Профиль",
            profile = ProfileTabUi(
                name = data?.employeeName ?: session?.userEmail.orEmpty(),
                email = data?.employeeEmail ?: "",
                roleTitle = UiLabels.roleTitle(primaryRole()),
                sections = sections
                    .distinct()
                    .filterNot { it == "home" || it == "profile" || it == "admin" }
                    .map { key ->
                        ProfileSectionUi(
                            key = key,
                            title = UiLabels.sectionTitle(key),
                            hint = SectionHints.forSection(key),
                        )
                    },
                adminAvailable = adminAvailable,
                showAdminButton = adminAvailable,
                showTrainerProfileEdit = showTrainerEdit,
                loading = data == null,
            ),
            errorMessage = null,
        )

        val extraSections = sections.filter {
            it !in setOf("home", "profile", "schedule", "dashboard", "admin", "clients", "app_support")
        }
        if (extraSections.isNotEmpty()) {
            runAsyncForTab(WorkUiState.TAB_PROFILE, "Загрузка...") {
                val section = extraSections.first()
                val items = withRefresh { token -> apiClient.loadList(token, section) }
                if (uiState.selectedTab != WorkUiState.TAB_PROFILE) return@runAsyncForTab ""
                runOnUiThread {
                    uiState = uiState.copy(
                        profile = uiState.profile.copy(
                            sectionTitle = UiLabels.sectionTitle(section),
                            items = items.take(10).map { feedToCard(it) },
                            loading = false,
                        ),
                    )
                }
                ""
            }
        } else if (data != null) {
            uiState = uiState.copy(profile = uiState.profile.copy(loading = false))
        }
    }

    private fun buildTicketActions(ticket: SupportTicketItem, allowWrite: Boolean): List<ActionUi> {
        val actions = mutableListOf<ActionUi>()
        if (ticket.clientId != null && sectionAllowed("clients")) {
            actions.add(ActionUi("ticket_client:${ticket.clientId}", "Карточка клиента"))
        }
        if (allowWrite && ticket.status != "done") {
            if (ticket.status == "new") {
                actions.add(ActionUi("ticket_status:${ticket.id}:in_progress", "Взять в работу"))
            }
            actions.add(ActionUi("ticket_status:${ticket.id}:done", "Закрыть обращение"))
        }
        return actions
    }

    private fun ticketToCard(ticket: SupportTicketItem): ListCardUi {
        val client = ticket.clientName.ifBlank { ticket.contactEmail.ifBlank { "Клиент не указан" } }
        val contact = listOf(ticket.contactEmail, ticket.clientPhone).filter { it.isNotBlank() }.joinToString(" · ")
        return ListCardUi(
            title = ticket.subject,
            subtitle = "Клиент: $client\n${ticket.message}",
            meta = "${UiLabels.ticketCategory(ticket.category)} · ${ticket.createdAt}" +
                if (contact.isNotBlank()) "\n$contact" else "",
            badge = UiLabels.ticketStatus(ticket.status),
            badgeColor = ticketBadgeColor(ticket.status),
            clientId = ticket.clientId,
            ticketId = ticket.id,
            refType = "ticket",
        )
    }

    private fun ticketBadgeColor(status: String): BadgeColor = when (status) {
        "new" -> BadgeColor.WARNING
        "in_progress" -> BadgeColor.PRIMARY
        "done" -> BadgeColor.SUCCESS
        else -> BadgeColor.NEUTRAL
    }

    private fun scheduleToCard(item: ScheduleItem): ListCardUi {
        val clients = if (item.clientNames.isNotEmpty()) {
            item.clientNames.joinToString(", ")
        } else {
            item.participants.ifBlank { "нет записей" }
        }
        return ListCardUi(
            title = "${item.startTime}–${item.endTime}  ${item.title}",
            subtitle = "Клиенты: $clients",
            meta = "${UiLabels.trainingType(item.type)} · ${item.trainer} · ${item.room}",
        )
    }

    private fun scheduleToSession(item: ScheduleItem): ScheduleSessionUi {
        val (booked, max) = parseParticipants(item.participants)
        return ScheduleSessionUi(
            trainingId = item.id,
            title = item.title,
            type = item.type,
            typeLabel = UiLabels.trainingType(item.type),
            startTime = item.startTime,
            endTime = item.endTime,
            durationMinutes = durationMinutes(item.startTime, item.endTime),
            trainer = item.trainer,
            room = item.room,
            bookedCount = item.currentParticipants ?: booked,
            maxParticipants = item.maxParticipants ?: max,
            clientNames = item.clientNames,
            bookings = item.bookings.map {
                ScheduleBookingUi(id = it.id, clientName = it.clientName, clientId = it.clientId)
            },
        )
    }

    private fun openAssignDialog(session: ScheduleSessionUi) {
        val trainingId = session.trainingId ?: return
        uiState = uiState.copy(
            assignDialog = AssignClientDialogUi(
                trainingId = trainingId,
                sessionTitle = "${session.startTime} ${session.title}",
                booked = session.bookings.map {
                    ListCardUi(title = it.clientName, meta = it.id)
                },
            ),
        )
        searchAssignClients()
    }

    private fun openCreateSessionDialog() {
        val date = selectedScheduleDate ?: todayDate()
        uiState = uiState.copy(
            createSessionDialog = CreateSessionDialogUi(date = date),
        )
    }

    private fun createSession() {
        val dialog = uiState.createSessionDialog ?: return
        val startTime = normalizeTime(dialog.startTime) ?: run {
            uiState = uiState.copy(
                createSessionDialog = dialog.copy(errorMessage = "Укажите время начала в формате ЧЧ:ММ"),
            )
            return
        }
        val endTime = normalizeTime(dialog.endTime) ?: run {
            uiState = uiState.copy(
                createSessionDialog = dialog.copy(errorMessage = "Укажите время окончания в формате ЧЧ:ММ"),
            )
            return
        }
        val max = dialog.maxParticipants.toIntOrNull()?.coerceAtLeast(1) ?: 1
        uiState = uiState.copy(createSessionDialog = dialog.copy(loading = true, errorMessage = null))
        thread {
            try {
                val created = withRefresh { token ->
                    apiClient.createTraining(
                        token = token,
                        name = dialog.name.trim().ifBlank { "Персональная тренировка" },
                        type = dialog.type,
                        startAtIso = "${dialog.date}T$startTime:00",
                        endAtIso = "${dialog.date}T$endTime:00",
                        room = dialog.room.trim().ifBlank { null },
                        maxParticipants = if (dialog.type == "personal") 1 else max,
                    )
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        createSessionDialog = null,
                        statusMessage = "Занятие создано",
                    )
                    selectedScheduleDate = created.date.ifBlank { dialog.date }
                    showScheduleTab()
                    scheduleToSession(created).let { openAssignDialog(it) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState.createSessionDialog?.let {
                        uiState = uiState.copy(
                            createSessionDialog = it.copy(
                                loading = false,
                                errorMessage = UserFacingError.message(e),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun normalizeTime(raw: String): String? {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").find(raw.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return "%02d:%02d".format(h, min)
    }

    private fun searchAssignClients() {
        val dialog = uiState.assignDialog ?: return
        uiState = uiState.copy(assignDialog = dialog.copy(loading = true, errorMessage = null))
        thread {
            try {
                val clients = withRefresh { apiClient.loadClients(it, dialog.query) }
                runOnUiThread {
                    val current = uiState.assignDialog ?: return@runOnUiThread
                    uiState = uiState.copy(
                        assignDialog = current.copy(
                            loading = false,
                            clients = clients.map {
                                ListCardUi(
                                    title = it.name,
                                    subtitle = listOf(it.phone, it.email).filter { s -> s.isNotBlank() }.joinToString(" · "),
                                    clientId = it.id,
                                )
                            },
                        ),
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState.assignDialog?.let {
                        uiState = uiState.copy(
                            assignDialog = it.copy(loading = false, errorMessage = UserFacingError.message(e)),
                        )
                    }
                }
            }
        }
    }

    private fun bookAssignClient(clientId: Int) {
        val dialog = uiState.assignDialog ?: return
        thread {
            try {
                withRefresh { apiClient.bookClientOnTraining(it, dialog.trainingId, clientId) }
                runOnUiThread {
                    uiState = uiState.copy(assignDialog = null, statusMessage = "Клиент записан")
                    showScheduleTab()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState.assignDialog?.let {
                        uiState = uiState.copy(
                            assignDialog = it.copy(errorMessage = UserFacingError.message(e)),
                        )
                    }
                }
            }
        }
    }

    private fun cancelAssignBooking(bookingId: String) {
        thread {
            try {
                withRefresh { apiClient.cancelStaffBooking(it, bookingId) }
                runOnUiThread {
                    uiState = uiState.copy(assignDialog = null, statusMessage = "Запись снята")
                    showScheduleTab()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    uiState.assignDialog?.let {
                        uiState = uiState.copy(
                            assignDialog = it.copy(errorMessage = UserFacingError.message(e)),
                        )
                    }
                }
            }
        }
    }

    private fun parseDayLabel(label: String): Pair<String, String> {
        val parts = label.trim().split(Regex("\\s+"))
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            parts.size == 1 -> "" to parts[0]
            else -> "" to ""
        }
    }

    private fun parseParticipants(participants: String): Pair<Int?, Int?> {
        val match = Regex("""(\d+)\s*/\s*(\d+)""").find(participants) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    private fun durationMinutes(start: String, end: String): Int {
        fun toMinutes(value: String): Int? {
            val parts = value.split(":")
            if (parts.size < 2) return null
            val hours = parts[0].toIntOrNull() ?: return null
            val minutes = parts[1].toIntOrNull() ?: return null
            return hours * 60 + minutes
        }
        val startMinutes = toMinutes(start)
        val endMinutes = toMinutes(end)
        if (startMinutes == null || endMinutes == null) return 60
        val diff = endMinutes - startMinutes
        return if (diff > 0) diff else 60
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

    private fun openClientCard(clientId: Int) {
        startActivity(
            Intent(this, ClientDetailActivity::class.java)
                .putExtra(ClientDetailActivity.EXTRA_CLIENT_ID, clientId),
        )
    }

    private fun updateTicketStatus(ticketId: Int, status: String) {
        runAsyncForTab(WorkUiState.TAB_SUPPORT, "Обновление статуса...") {
            withRefresh { token -> apiClient.updateSupportTicketStatus(token, ticketId, status) }
            showSupportTab()
            ""
        }
    }

    private fun canWriteSupport(): Boolean =
        config?.adminActions?.contains("admin.write") == true
            || config?.adminActions?.contains("support.write") == true

    private fun selectTab(tab: Int) {
        uiState = uiState.copy(selectedTab = tab, screenTitle = tabTitle(tab), errorMessage = null)
        when (tab) {
            WorkUiState.TAB_HOME -> showHomeTab()
            WorkUiState.TAB_SCHEDULE -> showScheduleTab()
            WorkUiState.TAB_PROFILE -> showProfileTab()
            WorkUiState.TAB_SUPPORT -> showSupportTab()
            WorkUiState.TAB_CLIENTS -> showClientsTab()
        }
    }

    private fun tabTitle(tab: Int): String = when (tab) {
        WorkUiState.TAB_HOME -> "Главная"
        WorkUiState.TAB_SCHEDULE -> "Расписание"
        WorkUiState.TAB_CLIENTS -> "Клиенты"
        WorkUiState.TAB_PROFILE -> "Профиль"
        WorkUiState.TAB_SUPPORT -> "Обращения"
        else -> "Главная"
    }

    private fun tabFromNavId(navId: Int): Int = when (navId) {
        R.id.nav_schedule -> WorkUiState.TAB_SCHEDULE
        R.id.nav_clients -> WorkUiState.TAB_CLIENTS
        R.id.nav_profile -> WorkUiState.TAB_PROFILE
        R.id.nav_support -> WorkUiState.TAB_SUPPORT
        else -> WorkUiState.TAB_HOME
    }

    private fun primaryRole(): String {
        val roles = (config?.roles.orEmpty() + appData?.roles.orEmpty()).distinct()
        val priority = listOf(
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_TRAINER", "ROLE_MANAGER",
            "ROLE_SUPPORT", "ROLE_FINANCE", "ROLE_VIEWER",
        )
        return priority.firstOrNull { roles.contains(it) }
            ?: roles.firstOrNull { it != "ROLE_STAFF" }
            ?: "ROLE_VIEWER"
    }

    private fun todayDate(): String {
        val cal = Calendar.getInstance()
        return String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun loadScheduleCached(forceRefresh: Boolean = false): ScheduleData {
        if (!forceRefresh) scheduleData?.let { return it }
        return withRefresh { token -> apiClient.loadSchedule(token) }.also { scheduleData = it }
    }

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

    private fun runAsyncForTab(tab: Int, @Suppress("UNUSED_PARAMETER") progress: String, action: () -> String) {
        val generation = ++loadGeneration
        thread {
            try {
                action()
                runOnUiThread {
                    if (generation != loadGeneration || uiState.selectedTab != tab) return@runOnUiThread
                    if (uiState.errorMessage != null) {
                        uiState = uiState.copy(errorMessage = null)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (generation != loadGeneration || uiState.selectedTab != tab) return@runOnUiThread
                    uiState = uiState.copy(errorMessage = UserFacingError.message(e))
                    if (tab == WorkUiState.TAB_SCHEDULE) scheduleData = null
                }
            }
        }
    }

    private fun runAsync(progress: String, action: () -> String) {
        runAsyncForTab(uiState.selectedTab, progress, action)
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        private const val REQUEST_NOTIFICATIONS = 42
        private val HOME_SECTIONS = setOf("bookings", "clients", "tasks", "subscriptions", "schedule", "app_support")
    }
}
