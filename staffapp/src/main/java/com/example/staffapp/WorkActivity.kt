package com.example.staffapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.staffapp.ui.phone.formatRussianPhoneMask
import com.example.staffapp.ui.phone.normalizeRussianNationalDigits
import com.example.staffapp.ui.theme.StaffTheme
import com.example.staffapp.ui.work.ActionUi
import com.example.staffapp.ui.work.AssignClientDialogUi
import com.example.staffapp.ui.work.CreateSessionDialogUi
import com.example.staffapp.ui.work.ProfileSectionUi
import com.example.staffapp.ui.work.SectionHints
import com.example.staffapp.ui.work.BadgeColor
import com.example.staffapp.ui.work.ClientsTabUi
import com.example.staffapp.ui.work.DayChipUi
import com.example.staffapp.ui.work.HomeSectionUi
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
import java.time.LocalDate
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
    private var scheduleDataFrom: String? = null
    private var scheduleFromDate: String? = null // null = окно от сегодняшнего дня
    private var selectedScheduleDate: String? = null
    private var selectedScheduleTypeFilter: String? = null
    private var selectedSupportFilter: String? = null
    private var clientsSearchQuery: String = ""
    private var clientsData: List<ClientSummary> = emptyList()
    private var assignDialogSession: ScheduleSessionUi? = null
    private var lastOnboarding: StaffOnboarding? = null

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            StaffPushRegistrar.registerIfLoggedIn(this)
        }
        refreshNotificationBanner()
    }

    private var loadGeneration = 0
    private var profileLoadGeneration = 0
    private var initialDataLoaded = false
    private val sessionLock = Any()

    private var uiState by mutableStateOf(WorkUiState())
    private var openLegalPdf by mutableStateOf<com.example.staffapp.legal.StaffLegalPdf?>(null)

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
                val pdf = openLegalPdf
                if (pdf != null) {
                    com.example.staffapp.ui.legal.LegalPdfScreen(
                        doc = pdf,
                        onNavigateBack = { openLegalPdf = null },
                    )
                } else {
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
                        selectedScheduleTypeFilter = if (filter == "group") null else filter
                        scheduleData?.let { renderSchedule(it) }
                    },
                    onSchedulePrevPeriod = { shiftSchedulePeriod(-7) },
                    onScheduleNextPeriod = { shiftSchedulePeriod(7) },
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
                    onClientsActiveFilterToggle = { toggleClientsActiveFilter() },
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
                    onAssignOpenClient = { openClientCard(it) },
                    onAssignEditSession = { openEditSessionDialog() },
                    onAssignDismiss = { uiState = uiState.copy(assignDialog = null) },
                    onCreateSessionClick = { openCreateSessionDialog() },
                    onCreateNameChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(name = v))
                        }
                    },
                    onCreateDateChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(date = v, errorMessage = null))
                        }
                    },
                    onCreateStartTimeChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(startTime = v, errorMessage = null))
                        }
                    },
                    onCreateDurationChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(durationMinutes = v, errorMessage = null))
                        }
                    },
                    onCreateRoomChange = { v ->
                        uiState.createSessionDialog?.let {
                            uiState = uiState.copy(createSessionDialog = it.copy(room = v))
                        }
                    },
                    onCreateConfirm = { createSession() },
                    onCreateDismiss = { uiState = uiState.copy(createSessionDialog = null) },
                    onNotificationPermissionResult = { granted ->
                        if (granted) {
                            StaffPushRegistrar.registerIfLoggedIn(this)
                        }
                        refreshNotificationBanner()
                    },
                )
                }
            }
        }

        StaffNotificationHelper.ensureChannel(this)
        StaffPushRegistrar.registerIfLoggedIn(this)
        thread {
            try {
                val onboarding = withRefresh { apiClient.loadOnboarding(it) }
                if (onboarding.status == "needs_profile") {
                    runOnUiThread {
                        startActivity(
                            Intent(this, TrainerProfileActivity::class.java)
                                .putExtra(TrainerProfileActivity.EXTRA_REQUIRED, true),
                        )
                        finish()
                    }
                    return@thread
                }
                if (onboarding.status != "active") {
                    runOnUiThread {
                        startActivity(Intent(this, OnboardingActivity::class.java))
                        finish()
                    }
                    return@thread
                }
                runOnUiThread {
                    lastOnboarding = onboarding
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
        // Клик по записи открывает саму запись в расписании,
        // а не карточку клиента (пункты 54/59 репорта).
        card.trainingId?.let { trainingId ->
            card.trainingDate?.takeIf { it.isNotBlank() }?.let { selectedScheduleDate = it }
            // Если расписание было пролистано на другую неделю — возвращаем окно
            // к дате занятия, иначе выбранный день не попадёт в видимый диапазон.
            ensureScheduleWindowContains(selectedScheduleDate)
            if (uiState.showScheduleNav) {
                selectTab(WorkUiState.TAB_SCHEDULE)
            }
            scheduleData?.items?.firstOrNull { it.id == trainingId }?.let {
                openAssignDialog(scheduleToSession(it))
            }
            return
        }
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
        if (sectionKey in HIDDEN_APP_SECTIONS) return
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
            actionId == "open_entry_qr" -> {
                val id = resolveStaffUserIdForQr()
                startActivity(
                    Intent(this, StaffEntryQrActivity::class.java).putExtra(
                        StaffEntryQrActivity.EXTRA_STAFF_USER_ID,
                        id,
                    ),
                )
            }
            actionId == "open_rental" -> startActivity(Intent(this, RentalActivity::class.java))
            actionId.startsWith("set_active_club:") -> {
                val clubId = actionId.removePrefix("set_active_club:").toIntOrNull()
                if (clubId != null && clubId > 0) {
                    setActiveRentalClub(clubId)
                }
            }
            actionId == "open_feedback" -> startActivity(Intent(this, StaffFeedbackActivity::class.java))
            actionId == "open_user_agreement" ->
                openLegalPdf = com.example.staffapp.legal.StaffLegalPdf.USER_AGREEMENT
            actionId == "open_privacy" ->
                openLegalPdf = com.example.staffapp.legal.StaffLegalPdf.PRIVACY
            actionId == "open_pro_offer" ->
                openLegalPdf = com.example.staffapp.legal.StaffLegalPdf.PRO_OFFER
            actionId == "open_docs" ->
                openExternalUrl(com.example.staffapp.legal.LegalPdfFiles.CLUB_DOCS_URL)
            actionId == "open_offer" ->
                openLegalPdf = com.example.staffapp.legal.StaffLegalPdf.PRO_OFFER
            actionId == "enable_notifications" -> requestOrOpenNotificationSettings()
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
        refreshNotificationBanner()
        if (session != null && allowedSections.contains("app_support")) {
            pollUnreadNotifications()
        }
        val isTrainer = primaryRole() == "ROLE_TRAINER"
            || (config?.roles.orEmpty() + appData?.roles.orEmpty()).contains("ROLE_TRAINER")
        if (session != null && isTrainer && uiState.selectedTab == WorkUiState.TAB_HOME) {
            refreshHomeEntryQr()
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

    private fun requestOrOpenNotificationSettings() {
        if (NotificationPermissionHelper.needsRuntimePrompt(this)) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            window.decorView.postDelayed({
                if (!NotificationPermissionHelper.notificationsEnabled(this) &&
                    NotificationPermissionHelper.shouldOpenSettings(this)
                ) {
                    NotificationPermissionHelper.openAppNotificationSettings(this)
                }
                refreshNotificationBanner()
            }, 800)
            return
        }
        if (!NotificationPermissionHelper.notificationsEnabled(this)) {
            NotificationPermissionHelper.openAppNotificationSettings(this)
        }
        refreshNotificationBanner()
    }

    private fun refreshNotificationBanner() {
        val need = !NotificationPermissionHelper.notificationsEnabled(this)
        if (uiState.home.needNotificationsPermission == need) return
        uiState = uiState.copy(home = uiState.home.copy(needNotificationsPermission = need))
    }

    private fun showHomeTab() {
        val isTrainer = primaryRole() == "ROLE_TRAINER"
            || (config?.roles.orEmpty() + appData?.roles.orEmpty()).contains("ROLE_TRAINER")
        val staffId = appData?.employeeId?.takeIf { it > 0 }
            ?: uiState.home.entryQrStaffUserId
        // QR не включаем «наугад»: только после проверки аренды в refreshHomeEntryQr().
        uiState = uiState.copy(
            screenTitle = "Главная",
            errorMessage = null,
            home = HomeTabUi(
                loading = appData == null,
                needNotificationsPermission = !NotificationPermissionHelper.notificationsEnabled(this),
                showEntryQr = isTrainer,
                entryQrStaffUserId = staffId,
                entryQrActive = false,
                entryQrBlockedMessage = if (isTrainer) {
                    "Проверяем оплату аренды…"
                } else {
                    null
                },
            ),
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
                needNotificationsPermission = !NotificationPermissionHelper.notificationsEnabled(this),
                showEntryQr = isTrainer,
                entryQrStaffUserId = data.employeeId.takeIf { it > 0 } ?: staffId,
                entryQrActive = false,
                entryQrBlockedMessage = if (isTrainer) {
                    "Проверяем оплату аренды…"
                } else {
                    null
                },
            ),
        )
        if (isTrainer) {
            refreshHomeEntryQr()
        }

        val homeSection = when (role) {
            "ROLE_TRAINER" -> "bookings"
            "ROLE_MANAGER" -> "tasks"
            "ROLE_FINANCE" -> "clients"
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
                            sections = listOf(
                                HomeSectionUi(
                                    title = "Новые обращения: ${tickets.newCount}",
                                    items = items,
                                    emptyMessage = if (items.isEmpty()) "Новых обращений нет" else null,
                                ),
                            ),
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
                    val sections = trainerHomeSections(schedule)
                    runOnUiThread {
                        uiState = uiState.copy(
                            home = uiState.home.copy(sections = sections, loading = false),
                        )
                    }
                } else {
                    val items = withRefresh { token -> apiClient.loadList(token, homeSection) }
                    if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runAsyncForTab ""
                    runOnUiThread {
                        uiState = uiState.copy(
                            home = uiState.home.copy(
                                sections = listOf(
                                    HomeSectionUi(
                                        title = UiLabels.sectionTitle(homeSection),
                                        items = items.take(8).map { feedToCard(it) },
                                        emptyMessage = if (items.isEmpty()) "Нет данных" else null,
                                    ),
                                ),
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
                val items = schedule.items.take(5).map { scheduleToCard(it, includeDate = true) }
                runOnUiThread {
                    uiState = uiState.copy(
                        home = uiState.home.copy(
                            sections = listOf(
                                HomeSectionUi(
                                    title = "Ближайшие тренировки",
                                    items = items,
                                    emptyMessage = if (items.isEmpty()) "Нет тренировок" else null,
                                ),
                            ),
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

    /** Id StaffUser для QR: onboarding → home state → app/data. */
    private fun resolveStaffUserIdForQr(): Int {
        return listOfNotNull(
            lastOnboarding?.staffUserId?.takeIf { it > 0 },
            uiState.home.entryQrStaffUserId.takeIf { it > 0 },
            appData?.employeeId?.takeIf { it > 0 },
        ).firstOrNull() ?: 0
    }

    /** Уточняет staffUserId / статус аренды (оплата + срок) для QR на главной. */
    private fun refreshHomeEntryQr() {
        // Сразу из кэша — не ждём сеть и не гасим QR при сбое запроса.
        lastOnboarding?.let { cached ->
            applyHomeEntryQr(
                staffUserId = resolveStaffUserIdForQr().takeIf { it > 0 }
                    ?: cached.staffUserId ?: 0,
                onboarding = cached,
            )
        }
        thread {
            try {
                val onboarding = withRefresh { token -> apiClient.loadOnboarding(token) }
                lastOnboarding = onboarding
                var id = listOfNotNull(
                    onboarding.staffUserId?.takeIf { it > 0 },
                    appData?.employeeId?.takeIf { it > 0 },
                    uiState.home.entryQrStaffUserId.takeIf { it > 0 },
                ).firstOrNull() ?: 0
                if (id <= 0) {
                    runCatching {
                        withRefresh { token -> apiClient.loadAppData(token).employeeId }
                    }.getOrNull()?.takeIf { it > 0 }?.let { loaded ->
                        id = loaded
                        appData = appData?.copy(employeeId = loaded) ?: appData
                    }
                }
                runOnUiThread {
                    if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runOnUiThread
                    applyHomeEntryQr(staffUserId = id, onboarding = onboarding)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (uiState.selectedTab != WorkUiState.TAB_HOME) return@runOnUiThread
                    val cached = lastOnboarding
                    val id = resolveStaffUserIdForQr()
                    if (cached != null && id > 0) {
                        applyHomeEntryQr(staffUserId = id, onboarding = cached)
                    } else if (uiState.home.entryQrStaffUserId <= 0) {
                        uiState = uiState.copy(
                            home = uiState.home.copy(
                                entryQrActive = false,
                                entryQrBlockedMessage = "Не удалось определить учётную запись.",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun applyHomeEntryQr(staffUserId: Int, onboarding: StaffOnboarding) {
        val paidClubs = onboarding.rentalClubs.filter { it.rentalActive }
        val hasPaidButInactive = onboarding.requiresRental &&
            paidClubs.isNotEmpty() &&
            !onboarding.activeClubRentalOk
        val active = StaffRentalAccess.canShowEntryQr(
            staffUserId = staffUserId,
            status = onboarding.status,
            requiresRental = onboarding.requiresRental,
            rentalPaidUntilIso = onboarding.activeClubPaidUntil,
            rentalActiveFromServer = onboarding.rentalActive,
            activeClubRentalOk = onboarding.activeClubRentalOk,
        )
        val blocked = StaffRentalAccess.entryQrBlockedMessage(
            staffUserId = staffUserId,
            status = onboarding.status,
            requiresRental = onboarding.requiresRental,
            rentalPaidUntilIso = onboarding.activeClubPaidUntil,
            hasPaidClubsButWrongActive = hasPaidButInactive,
        )
        uiState = uiState.copy(
            home = uiState.home.copy(
                showEntryQr = true,
                entryQrStaffUserId = staffUserId.takeIf { it > 0 } ?: uiState.home.entryQrStaffUserId,
                entryQrActive = active,
                entryQrBlockedMessage = blocked,
            ),
        )
    }

    private fun setActiveRentalClub(clubId: Int) {
        runAsyncForTab(WorkUiState.TAB_PROFILE, "Меняем зал...") {
            val onboarding = withRefresh { token -> apiClient.setActiveRentalClub(token, clubId) }
            lastOnboarding = onboarding
            runOnUiThread {
                applyProfileRentalState(onboarding)
                applyHomeEntryQr(
                    onboarding.staffUserId ?: uiState.home.entryQrStaffUserId,
                    onboarding,
                )
            }
            "Адрес обновлён"
        }
    }

    private fun applyProfileRentalState(onboarding: StaffOnboarding) {
        val active = onboarding.activeClub
        uiState = uiState.copy(
            profile = uiState.profile.copy(
                rentalPaidUntilLabel = formatRentalUntil(
                    active?.paidUntil ?: onboarding.rentalPaidUntil,
                    active?.title,
                ),
                paidRentalClubs = onboarding.rentalClubs.filter { it.rentalActive },
                activeClubId = onboarding.activeClubId,
                offerUrl = onboarding.offerUrl,
                privacyUrl = onboarding.privacyUrl,
                docsUrl = onboarding.docsUrl,
            ),
        )
    }

    /**
     * Дашборд тренера (пункт 53 репорта): всегда показываем «сегодня», при наличии —
     * «завтра», а если пусто и там и там — ближайшие записи с датой.
     */
    private fun trainerHomeSections(schedule: ScheduleData): List<HomeSectionUi> {
        val today = todayDate()
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val todayItems = schedule.items.filter { it.date == today }.map { scheduleToCard(it) }
        val tomorrowItems = schedule.items.filter { it.date == tomorrow }.map { scheduleToCard(it) }
        val sections = mutableListOf(
            HomeSectionUi(
                title = "Записи на сегодня",
                items = todayItems,
                emptyMessage = if (todayItems.isEmpty()) "На сегодня записей нет — можно отдохнуть" else null,
            ),
        )
        if (tomorrowItems.isNotEmpty()) {
            sections += HomeSectionUi(title = "Записи на завтра", items = tomorrowItems)
        }
        if (todayItems.isEmpty() && tomorrowItems.isEmpty()) {
            val upcoming = schedule.items
                .filter { it.date > today }
                .take(5)
                .map { scheduleToCard(it, includeDate = true) }
            if (upcoming.isNotEmpty()) {
                sections += HomeSectionUi(title = "Ближайшие записи", items = upcoming)
            }
        }
        return sections
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
            val schedule = loadScheduleCached(forceRefresh = true, from = scheduleFromDate)
            val dates = schedule.days.map { it.date }
            if (selectedScheduleDate == null || selectedScheduleDate !in dates) {
                selectedScheduleDate = dates.firstOrNull { it == todayDate() } ?: dates.firstOrNull()
            }
            scheduleData = schedule
            if (uiState.selectedTab != WorkUiState.TAB_SCHEDULE) return@runAsyncForTab ""
            runOnUiThread { renderSchedule(schedule) }
            ""
        }
    }

    /**
     * Сдвигает окно расписания так, чтобы указанная дата попадала в его 14 дней —
     * иначе созданное/перенесённое занятие «исчезает» из вида.
     */
    private fun ensureScheduleWindowContains(dateIso: String?) {
        val target = dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val windowStart = scheduleFromDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        if (target.isBefore(windowStart) || !target.isBefore(windowStart.plusDays(14))) {
            scheduleFromDate = if (target == LocalDate.now()) null else target.toString()
        }
    }

    private fun shiftSchedulePeriod(days: Int) {
        val current = scheduleFromDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val newFrom = current.plusDays(days.toLong())
        scheduleFromDate = if (newFrom == LocalDate.now()) null else newFrom.toString()
        selectedScheduleDate = null
        showScheduleTab()
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
            .filter { it.type != "group" }
            .filter { typeFilter == null || it.type == typeFilter }
        uiState = uiState.copy(
            schedule = ScheduleTabUi(
                days = days,
                sessions = dayItems.map { scheduleToSession(it) },
                monthLabel = scheduleMonthLabel(schedule.days.map { it.date }),
                selectedTypeFilter = typeFilter,
                loading = false,
            ),
        )
    }

    private fun scheduleMonthLabel(dates: List<String>): String {
        val first = dates.firstOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
        val last = dates.lastOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: first
        val firstMonth = MONTH_NAMES[first.monthValue - 1]
        val lastMonth = MONTH_NAMES[last.monthValue - 1]
        return when {
            first.month == last.month && first.year == last.year -> "$firstMonth ${first.year}"
            first.year == last.year -> "$firstMonth — $lastMonth ${first.year}"
            else -> "$firstMonth ${first.year} — $lastMonth ${last.year}"
        }
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
                clientsData = clients
                renderClientsList()
            }
            ""
        }
    }

    private fun renderClientsList() {
        val onlyActive = uiState.clients.onlyActiveBooking
        // Сначала клиенты с активной записью, внутри групп — по имени (пункт 31 репорта).
        val visible = clientsData
            .filter { !onlyActive || it.hasActiveBooking }
            .sortedWith(compareByDescending<ClientSummary> { it.hasActiveBooking }.thenBy { it.name.lowercase() })
        uiState = uiState.copy(
            clients = uiState.clients.copy(
                summary = if (visible.isEmpty()) "" else "Найдено: ${visible.size}",
                items = visible.map { client ->
                    ListCardUi(
                        title = client.name.ifBlank { "Клиент #${client.id}" },
                        // Контакты не выносим в список — они внутри карточки клиента.
                        meta = "Открыть карточку",
                        badge = if (client.hasActiveBooking) "Есть запись" else null,
                        badgeColor = BadgeColor.SUCCESS,
                        clientId = client.id,
                    )
                },
                loading = false,
            ),
        )
    }

    private fun toggleClientsActiveFilter() {
        uiState = uiState.copy(
            clients = uiState.clients.copy(onlyActiveBooking = !uiState.clients.onlyActiveBooking),
        )
        renderClientsList()
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
                // Данные тренера из прошлой загрузки не сбрасываем, чтобы карточка
                // не «мигала» пустой при каждом заходе на вкладку.
                phone = uiState.profile.phone,
                specialization = uiState.profile.specialization,
                description = uiState.profile.description,
                photoUrl = uiState.profile.photoUrl,
                rentalPaidUntilLabel = formatRentalUntil(
                    lastOnboarding?.activeClubPaidUntil ?: lastOnboarding?.rentalPaidUntil,
                    lastOnboarding?.activeClub?.title,
                ) ?: uiState.profile.rentalPaidUntilLabel,
                paidRentalClubs = lastOnboarding?.rentalClubs?.filter { it.rentalActive }
                    ?: uiState.profile.paidRentalClubs,
                activeClubId = lastOnboarding?.activeClubId ?: uiState.profile.activeClubId,
                offerUrl = lastOnboarding?.offerUrl ?: uiState.profile.offerUrl,
                privacyUrl = lastOnboarding?.privacyUrl ?: uiState.profile.privacyUrl,
                docsUrl = lastOnboarding?.docsUrl ?: uiState.profile.docsUrl,
                sections = sections
                    .distinct()
                    .filterNot {
                        it == "home" || it == "profile" || it == "admin" || it in HIDDEN_APP_SECTIONS
                    }
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
                showClubEntryQr = showTrainerEdit,
                showRentalManage = showTrainerEdit,
                showFeedback = true,
                loading = data == null,
            ),
            errorMessage = null,
        )

        // Карточка тренера: подтягиваем публичный профиль (фото, специализация,
        // телефон), чтобы раздел показывал данные, а не дублировал меню (пункт 27).
        if (showTrainerEdit) {
            val generation = ++profileLoadGeneration
            thread {
                try {
                    val onboarding = withRefresh { token -> apiClient.loadOnboarding(token) }
                    lastOnboarding = onboarding
                    val trainerProfile = withRefresh { token -> apiClient.loadTrainerProfile(token) }
                    runOnUiThread {
                        if (generation != profileLoadGeneration ||
                            uiState.selectedTab != WorkUiState.TAB_PROFILE
                        ) {
                            return@runOnUiThread
                        }
                        uiState = uiState.copy(
                            profile = uiState.profile.copy(
                                name = trainerProfile.name.ifBlank { uiState.profile.name },
                                phone = formatPhoneForDisplay(trainerProfile.phone),
                                specialization = trainerProfile.specialization,
                                description = trainerProfile.description,
                                photoUrl = trainerProfile.photoUrl,
                                rentalPaidUntilLabel = formatRentalUntil(
                                    onboarding.activeClubPaidUntil,
                                    onboarding.activeClub?.title,
                                ),
                                paidRentalClubs = onboarding.rentalClubs.filter { it.rentalActive },
                                activeClubId = onboarding.activeClubId,
                                offerUrl = onboarding.offerUrl,
                                privacyUrl = onboarding.privacyUrl,
                                docsUrl = onboarding.docsUrl,
                            ),
                        )
                    }
                } catch (_: Exception) {
                    // Карточка тренера — дополнение; ошибки загрузки не блокируют раздел.
                }
            }
        }

        val extraSections = sections.filter {
            it !in setOf("home", "profile", "schedule", "dashboard", "admin", "clients", "app_support")
                && it !in HIDDEN_APP_SECTIONS
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

    private fun formatPhoneForDisplay(phone: String): String {
        val national = normalizeRussianNationalDigits(phone)
        return if (national.length == 10) formatRussianPhoneMask(national) else phone
    }

    private fun formatRentalUntil(iso: String?, clubTitle: String? = null): String? {
        if (iso.isNullOrBlank()) return null
        val date = runCatching {
            java.time.LocalDateTime.parse(iso.replace(' ', 'T').take(19))
        }.getOrNull()?.toLocalDate()
            ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
            ?: return null
        val until = "до ${date.dayOfMonth.toString().padStart(2, '0')}." +
            "${date.monthValue.toString().padStart(2, '0')}.${date.year}"
        return if (!clubTitle.isNullOrBlank()) {
            "Зал: $clubTitle · оплачен $until"
        } else {
            "Аренда оплачена $until"
        }
    }

    private fun openExternalUrl(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
            uiState = uiState.copy(errorMessage = "Не удалось открыть ссылку")
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

    private fun scheduleToCard(item: ScheduleItem, includeDate: Boolean = false): ListCardUi {
        val clients = if (item.clientNames.isNotEmpty()) {
            item.clientNames.joinToString(", ")
        } else {
            item.participants.ifBlank { "нет записей" }
        }
        val datePrefix = if (includeDate) {
            item.dayLabel.ifBlank { item.date }.let { "$it · " }
        } else {
            ""
        }
        return ListCardUi(
            title = "$datePrefix${item.startTime}–${item.endTime}  ${item.title}",
            subtitle = "Клиенты: $clients",
            meta = "${UiLabels.trainingType(item.type)} · ${item.trainer} · ${item.room}",
            trainingId = item.id,
            trainingDate = item.date,
        )
    }

    private fun scheduleToSession(item: ScheduleItem): ScheduleSessionUi {
        val (booked, max) = parseParticipants(item.participants)
        return ScheduleSessionUi(
            trainingId = item.id,
            date = item.date,
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
        assignDialogSession = session
        uiState = uiState.copy(
            assignDialog = AssignClientDialogUi(
                trainingId = trainingId,
                sessionTitle = "${session.startTime} ${session.title}",
                booked = session.bookings.map {
                    ListCardUi(
                        title = it.clientName,
                        meta = it.id,
                        clientId = it.clientId?.removePrefix("user-")?.toIntOrNull(),
                    )
                },
            ),
        )
        searchAssignClients()
    }

    private fun openEditSessionDialog() {
        val session = assignDialogSession ?: return
        val trainingId = session.trainingId ?: return
        uiState = uiState.copy(
            assignDialog = null,
            createSessionDialog = CreateSessionDialogUi(
                date = session.date.ifBlank { selectedScheduleDate ?: todayDate() },
                name = session.title,
                startTime = session.startTime,
                durationMinutes = session.durationMinutes,
                room = session.room.takeIf { it.isNotBlank() && it != "—" }.orEmpty(),
                editingTrainingId = trainingId,
            ),
        )
    }

    private fun openCreateSessionDialog() {
        val date = selectedScheduleDate ?: todayDate()
        uiState = uiState.copy(
            createSessionDialog = CreateSessionDialogUi(date = date),
        )
    }

    private fun createSession() {
        val dialog = uiState.createSessionDialog ?: return
        fun showError(message: String) {
            uiState = uiState.copy(createSessionDialog = dialog.copy(errorMessage = message))
        }

        val date = runCatching { LocalDate.parse(dialog.date) }.getOrNull() ?: run {
            showError("Выберите дату занятия")
            return
        }
        val start = normalizeTime(dialog.startTime)?.let {
            runCatching { java.time.LocalTime.parse(it) }.getOrNull()
        } ?: run {
            showError("Выберите время начала")
            return
        }
        if (dialog.durationMinutes <= 0) {
            showError("Выберите длительность занятия")
            return
        }
        val startDateTime = date.atTime(start)
        // Для редактирования прошлое не блокируем: тренер может поправить
        // название или зал уже прошедшего занятия.
        if (!dialog.isEditing && startDateTime.isBefore(java.time.LocalDateTime.now())) {
            showError("Нельзя создать занятие в прошлом. Проверьте дату и время.")
            return
        }
        val end = start.plusMinutes(dialog.durationMinutes.toLong())
        if (!end.isAfter(start)) {
            showError("Занятие должно заканчиваться в тот же день. Уменьшите длительность или измените время начала.")
            return
        }
        val startTime = "%02d:%02d".format(start.hour, start.minute)
        val endTime = "%02d:%02d".format(end.hour, end.minute)
        uiState = uiState.copy(createSessionDialog = dialog.copy(loading = true, errorMessage = null))
        thread {
            try {
                val editingId = dialog.editingTrainingId
                if (editingId != null) {
                    val updated = withRefresh { token ->
                        apiClient.updateTraining(
                            token = token,
                            trainingId = editingId,
                            name = dialog.name.trim().ifBlank { "Персональная тренировка" },
                            startAtIso = "${dialog.date}T$startTime:00",
                            endAtIso = "${dialog.date}T$endTime:00",
                            room = dialog.room.trim().ifBlank { null },
                        )
                    }
                    runOnUiThread {
                        uiState = uiState.copy(
                            createSessionDialog = null,
                            statusMessage = "Занятие обновлено",
                        )
                        selectedScheduleDate = updated.date.ifBlank { dialog.date }
                        ensureScheduleWindowContains(selectedScheduleDate)
                        showScheduleTab()
                    }
                    return@thread
                }
                val created = withRefresh { token ->
                    apiClient.createTraining(
                        token = token,
                        name = dialog.name.trim().ifBlank { "Персональная тренировка" },
                        type = "personal",
                        startAtIso = "${dialog.date}T$startTime:00",
                        endAtIso = "${dialog.date}T$endTime:00",
                        room = dialog.room.trim().ifBlank { null },
                        maxParticipants = 1,
                    )
                }
                runOnUiThread {
                    uiState = uiState.copy(
                        createSessionDialog = null,
                        statusMessage = "Занятие создано",
                    )
                    selectedScheduleDate = created.date.ifBlank { dialog.date }
                    ensureScheduleWindowContains(selectedScheduleDate)
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
                val trainingRemoved = withRefresh { apiClient.cancelStaffBooking(it, bookingId) }
                runOnUiThread {
                    uiState = uiState.copy(
                        assignDialog = null,
                        statusMessage = if (trainingRemoved) {
                            "Запись снята, занятие убрано из расписания"
                        } else {
                            "Запись снята"
                        },
                    )
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

    private fun loadScheduleCached(forceRefresh: Boolean = false, from: String? = null): ScheduleData {
        if (!forceRefresh && scheduleDataFrom == from) scheduleData?.let { return it }
        return withRefresh { token -> apiClient.loadSchedule(token, from) }.also {
            scheduleData = it
            scheduleDataFrom = from
        }
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
        private val HOME_SECTIONS = setOf("bookings", "clients", "tasks", "schedule", "app_support")
        private val HIDDEN_APP_SECTIONS = setOf("visits", "subscriptions")
        private val MONTH_NAMES = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )
    }
}
