import Foundation
import Observation
import UIKit
import UserNotifications

@Observable
@MainActor
final class WorkController {
    var state = WorkUiState()
    private let env: AppEnvironment

    private var appData: StaffAppData?
    private var lastOnboarding: StaffOnboarding?
    private var allowedSections: [String] = []
    private var scheduleData: ScheduleData?
    private var scheduleFromDate: String?
    private var selectedScheduleDate: String?
    private var selectedScheduleTypeFilter: String?
    private var selectedSupportFilter: String?
    private var clientsSearchQuery = ""
    private var clientsData: [ClientSummary] = []
    private var assignDialogSession: ScheduleSessionUi?
    private var loadGeneration = 0
    private var initialDataLoaded = false
    private var profileLoadGeneration = 0

    private static let homeSections: Set<String> = [
        "bookings", "clients", "tasks", "subscriptions", "schedule", "app_support",
    ]
    private static let hiddenAppSections: Set<String> = ["visits", "subscriptions"]
    private static let monthNames = [
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    ]

    var onOpenAdmin: (() -> Void)?
    var onOpenClient: ((Int) -> Void)?
    var onOpenAdminSection: ((String) -> Void)?
    var onOpenEntryQr: (() -> Void)?
    var onOpenRental: (() -> Void)?
    var onOpenFeedback: (() -> Void)?
    var onOpenTrainerProfile: (() -> Void)?
    var onOpenLegalPdf: ((StaffLegalPdf) -> Void)?

    /// Сессия из assign-диалога — источник правды для «Изменить время» (в т.ч. с главной).
    var assignSessionForEdit: ScheduleSessionUi? { assignDialogSession }

    init(env: AppEnvironment, initialTab: WorkTab = .home) {
        self.env = env
        state.selectedTab = initialTab
        state.screenTitle = initialTab.title
        updateNavVisibility()
    }

    func onAppear() {
        selectTab(state.selectedTab)
        loadData()
        pollUnreadNotifications()
    }

    func onResume() {
        refreshNotificationPermissionBanner()
        if env.session != nil, allowedSections.contains("app_support") {
            pollUnreadNotifications()
        }
    }

    func logout() {
        env.logout()
    }

    func selectTab(_ tab: WorkTab) {
        state.selectedTab = tab
        state.screenTitle = tab.title
        state.errorMessage = nil
        switch tab {
        case .home: showHomeTab()
        case .schedule: showScheduleTab()
        case .profile: showProfileTab()
        case .support: showSupportTab()
        case .clients: showClientsTab()
        }
    }

    func handleAction(_ actionId: String) {
        switch actionId {
        case "open_admin":
            onOpenAdmin?()
        case "open_entry_qr":
            onOpenEntryQr?()
        case "open_rental":
            onOpenRental?()
        case "open_feedback":
            onOpenFeedback?()
        case "open_trainer_profile", "edit_trainer_profile":
            onOpenTrainerProfile?()
        case "open_user_agreement":
            onOpenLegalPdf?(.userAgreement)
        case "open_privacy":
            onOpenLegalPdf?(.privacy)
        case "open_pro_offer", "open_offer":
            onOpenLegalPdf?(.proOffer)
        case "open_docs":
            openExternalUrl(state.profile.docsUrl)
        case "open_legal_pro_offer":
            onOpenLegalPdf?(.proOffer)
        case "open_legal_privacy":
            onOpenLegalPdf?(.dobrozalPrivacy)
        case "enable_notifications":
            requestOrOpenNotificationSettings()
        case "retry":
            selectTab(state.selectedTab)
        case "mark_notifications_read":
            runAsyncForTab(state.selectedTab) {
                _ = try await self.env.withRefresh { token in
                    try await self.env.apiClient.markAllStaffNotificationsRead(token: token)
                }
                self.showSupportTab()
            }
        default:
            if actionId.hasPrefix("set_active_club:") {
                let idStr = actionId.replacingOccurrences(of: "set_active_club:", with: "")
                if let clubId = Int(idStr) {
                    setActiveRentalClub(clubId)
                }
            } else if actionId.hasPrefix("ticket_status:") {
                let parts = actionId.split(separator: ":")
                if parts.count == 3, let ticketId = Int(parts[1]) {
                    updateTicketStatus(ticketId, status: String(parts[2]))
                }
            } else if actionId.hasPrefix("ticket_client:") {
                let idStr = actionId.replacingOccurrences(of: "ticket_client:", with: "")
                if let clientId = Int(idStr) {
                    onOpenClient?(clientId)
                }
            }
        }
    }

    func handleListCardClick(_ card: ListCardUi) {
        if let trainingId = card.trainingId, !trainingId.isEmpty {
            if let date = card.trainingDate, !date.isEmpty {
                selectedScheduleDate = date
            }
            ensureScheduleWindowContains(selectedScheduleDate)
            if state.showScheduleNav {
                selectTab(.schedule)
            }
            if let item = scheduleData?.items.first(where: { $0.id == trainingId }) {
                openAssignDialog(for: scheduleToSession(item))
            } else {
                Task { @MainActor in
                    do {
                        let schedule = try await self.loadScheduleCached(forceRefresh: true)
                        guard let item = schedule.items.first(where: { $0.id == trainingId }) else { return }
                        self.openAssignDialog(for: self.scheduleToSession(item))
                    } catch {}
                }
            }
            return
        }
        if let clientId = card.clientId {
            onOpenClient?(clientId)
            return
        }
        switch card.refType {
        case "client":
            if let id = card.feedId { onOpenClient?(id) }
        case "ticket":
            selectTab(.support)
        default:
            break
        }
    }

    func handleProfileSectionClick(_ sectionKey: String) {
        if Self.hiddenAppSections.contains(sectionKey) { return }
        switch sectionKey {
        case "schedule":
            if state.showScheduleNav { selectTab(.schedule) }
        case "clients":
            if state.showClientsNav { selectTab(.clients) }
        case "app_support":
            if state.showSupportNav { selectTab(.support) }
        default:
            if env.roleConfig?.adminSections.contains(sectionKey) == true {
                onOpenAdminSection?(sectionKey)
            }
        }
    }

    func onScheduleDaySelected(_ date: String) {
        selectedScheduleDate = date
        if let scheduleData { renderSchedule(scheduleData) }
    }

    func onScheduleTypeFilterSelected(_ filter: String?) {
        selectedScheduleTypeFilter = filter
        if let scheduleData { renderSchedule(scheduleData) }
    }

    func onSupportFilterSelected(_ filter: String) {
        selectedSupportFilter = filter.isEmpty ? nil : filter
        showSupportTab()
    }

    func onClientSearchQueryChange(_ query: String) {
        clientsSearchQuery = query
        state.clients.query = query
    }

    func onClientSearch() {
        clientsSearchQuery = state.clients.query
        loadClientsList(clientsSearchQuery)
    }

    // MARK: - Data loading

    private func loadData() {
        runAsync("Загрузка...") {
            let data = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadAppData(token: token)
            }
            self.appData = data
            self.allowedSections = data.sections
            self.initialDataLoaded = true
            self.env.registerPushIfLoggedIn()
            self.pollUnreadNotifications()
            self.updateNavVisibility()
            await self.refreshEntryQrState()
            self.refreshActiveTab()
        }
    }

    private func refreshEntryQrState() async {
        do {
            let onboarding = try await env.withRefresh { token in
                try await env.apiClient.loadOnboarding(token: token)
            }
            // Как Android WorkActivity: если статус ушёл с active — выходим из Work.
            if onboarding.status != "active" {
                await env.resolveAuthGate()
                return
            }
            lastOnboarding = onboarding
            let staffId = [
                onboarding.staffUserId,
                appData?.employeeId,
                state.home.entryQrStaffUserId,
            ].compactMap { $0 }.first { $0 > 0 } ?? 0
            applyHomeEntryQr(staffUserId: staffId, onboarding: onboarding)
            applyProfileRentalState(onboarding)
        } catch {
            // QR optional if onboarding endpoint fails
        }
    }

    private func applyHomeEntryQr(staffUserId: Int, onboarding: StaffOnboarding) {
        let paidClubs = onboarding.rentalClubs.filter(\.rentalActive)
        let hasPaidButInactive = onboarding.requiresRental &&
            !paidClubs.isEmpty &&
            !onboarding.activeClubRentalOk
        let active = StaffRentalAccess.canShowEntryQr(
            staffUserId: staffUserId,
            status: onboarding.status,
            requiresRental: onboarding.requiresRental,
            rentalPaidUntilIso: onboarding.activeClubPaidUntil,
            rentalActiveFromServer: onboarding.rentalActive,
            activeClubRentalOk: onboarding.activeClubRentalOk
        )
        let blocked = StaffRentalAccess.entryQrBlockedMessage(
            staffUserId: staffUserId,
            status: onboarding.status,
            requiresRental: onboarding.requiresRental,
            rentalPaidUntilIso: onboarding.activeClubPaidUntil,
            hasPaidClubsButWrongActive: hasPaidButInactive
        )
        let isTrainer = primaryRole() == "ROLE_TRAINER" || onboarding.requiresRental
        state.home.showEntryQr = isTrainer
        state.home.entryQrStaffUserId = staffUserId > 0 ? staffUserId : state.home.entryQrStaffUserId
        state.home.entryQrActive = active
        state.home.entryQrBlockedMessage = blocked
        state.home.entryQrFormat = onboarding.resolvedEntryQrFormat
    }

    private func applyProfileRentalState(_ onboarding: StaffOnboarding) {
        let active = onboarding.activeClub
        state.profile.rentalPaidUntilLabel = formatRentalUntil(
            active?.paidUntil ?? onboarding.rentalPaidUntil,
            title: active?.title
        )
        state.profile.paidRentalClubs = onboarding.rentalClubs.filter(\.rentalActive)
        state.profile.activeClubId = onboarding.activeClubId
        state.profile.offerUrl = onboarding.offerUrl
        state.profile.privacyUrl = onboarding.privacyUrl
        state.profile.docsUrl = onboarding.docsUrl
    }

    private func formatRentalUntil(_ iso: String?, title: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let day = String(iso.prefix(10))
        if let title, !title.isEmpty {
            return "Активен: \(title) · до \(day)"
        }
        return "Оплачено до \(day)"
    }

    private func setActiveRentalClub(_ clubId: Int) {
        runAsyncForTab(.profile) {
            let onboarding = try await self.env.withRefresh { token in
                try await self.env.apiClient.setActiveRentalClub(token: token, clubId: clubId)
            }
            self.lastOnboarding = onboarding
            self.applyProfileRentalState(onboarding)
            let staffId = onboarding.staffUserId ?? self.state.home.entryQrStaffUserId
            self.applyHomeEntryQr(staffUserId: staffId, onboarding: onboarding)
            self.state.statusMessage = "Адрес обновлён"
        }
    }

    func createTrainingSession(
        name: String,
        type: String,
        date: Date,
        startTime: Date,
        durationMinutes: Int,
        room: String,
        maxParticipants: Int
    ) async throws {
        let cal = Calendar.current
        let day = cal.dateComponents([.year, .month, .day], from: date)
        let time = cal.dateComponents([.hour, .minute], from: startTime)
        guard let y = day.year, let m = day.month, let d = day.day,
              let hour = time.hour, let minute = time.minute else {
            throw StaffApiError.parseFailed("Некорректная дата")
        }
        if durationMinutes <= 0 {
            throw StaffApiError.parseFailed("Выберите длительность занятия")
        }
        var startComponents = DateComponents()
        startComponents.year = y
        startComponents.month = m
        startComponents.day = d
        startComponents.hour = hour
        startComponents.minute = minute
        guard let start = cal.date(from: startComponents),
              let end = cal.date(byAdding: .minute, value: durationMinutes, to: start) else {
            throw StaffApiError.parseFailed("Некорректная дата")
        }
        let endParts = cal.dateComponents([.year, .month, .day, .hour, .minute], from: end)
        // Как Android: занятие не должно переходить через полночь.
        if endParts.year != y || endParts.month != m || endParts.day != d || !endParts.isAfterSameDay(hour: hour, minute: minute) {
            throw StaffApiError.parseFailed("Занятие должно заканчиваться в тот же день. Уменьшите длительность или измените время начала.")
        }
        if start < Date() {
            throw StaffApiError.parseFailed("Нельзя создать занятие в прошлом. Проверьте дату и время.")
        }
        let dateLabel = String(format: "%04d-%02d-%02d", y, m, d)
        let startTimeLabel = String(format: "%02d:%02d", hour, minute)
        let endTimeLabel = String(format: "%02d:%02d", endParts.hour ?? 0, endParts.minute ?? 0)
        // Wall-clock как Android: `yyyy-MM-dd'T'HH:mm:ss` без TZ-сдвига.
        let startAtIso = "\(dateLabel)T\(startTimeLabel):00"
        let endAtIso = "\(dateLabel)T\(endTimeLabel):00"
        let roomTrimmed = room.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameTrimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = nameTrimmed.isEmpty ? "Персональная тренировка" : nameTrimmed
        _ = type
        _ = maxParticipants
        let created = try await env.withRefresh { token in
            try await env.apiClient.createTraining(
                token: token,
                name: resolvedName,
                type: "personal",
                startAtIso: startAtIso,
                endAtIso: endAtIso,
                room: roomTrimmed.isEmpty ? nil : roomTrimmed,
                maxParticipants: 1
            )
        }
        scheduleData = nil
        let focusDate = created.date.isEmpty ? dateLabel : created.date
        selectedScheduleDate = focusDate
        ensureScheduleWindowContains(focusDate)
        if state.selectedTab == .schedule {
            let schedule = try await loadScheduleCached(forceRefresh: true)
            renderSchedule(schedule)
        } else {
            showScheduleTab()
        }
        openAssignDialog(for: scheduleToSession(created))
        state.statusMessage = "Занятие создано"
    }

    private func refreshActiveTab() {
        selectTab(state.selectedTab)
    }

    private func updateNavVisibility() {
        let config = env.sessionStore.loadConfig() ?? env.roleConfig
        env.roleConfig = config
        let sections = config?.appSections ?? []
        let adminSections = config?.adminSections ?? []
        state.showScheduleNav = sections.contains("schedule") || adminSections.contains("schedule")
        state.showClientsNav = sections.contains("clients") || adminSections.contains("clients")
        state.showSupportNav = sections.contains("app_support") || adminSections.contains("app_support")
    }

    private func pollUnreadNotifications() {
        guard allowedSections.contains("app_support") else { return }
        Task {
            do {
                let notifications = try await env.withRefresh { token in
                    try await env.apiClient.loadStaffNotifications(token: token)
                }
                let previous = env.sessionStore.getLastUnreadNotificationCount()
                if previous >= 0, notifications.unreadCount > previous {
                    let latest = notifications.items.first { !$0.isRead }
                    StaffNotificationService.shared.showSupportNotification(
                        title: latest?.title ?? "Новое обращение",
                        body: latest?.body ?? "Появилось новое обращение из приложения"
                    )
                }
                env.sessionStore.setLastUnreadNotificationCount(notifications.unreadCount)
            } catch {}
        }
    }

    private func showHomeTab() {
        state.screenTitle = "Главная"
        state.errorMessage = nil
        let preservedQr = state.home
        let isTrainer = primaryRole() == "ROLE_TRAINER"
            || (env.roleConfig?.roles ?? []).contains("ROLE_TRAINER")
        state.home = HomeTabUi(
            showEntryQr: preservedQr.showEntryQr,
            entryQrStaffUserId: preservedQr.entryQrStaffUserId,
            entryQrActive: preservedQr.entryQrActive,
            entryQrBlockedMessage: preservedQr.entryQrBlockedMessage,
            entryQrFormat: preservedQr.entryQrFormat,
            needNotificationsPermission: preservedQr.needNotificationsPermission,
            loading: appData == nil
        )
        refreshNotificationPermissionBanner()
        guard let data = appData else { return }

        let role = primaryRole()
        let showAdmin = env.roleConfig?.adminActions.contains("admin.write") == true
            || role == "ROLE_ADMIN"
            || role == "ROLE_SUPER_ADMIN"
            || role == "ROLE_MANAGER"
            || !(env.roleConfig?.adminSections.isEmpty ?? true)
            || allowedSections.contains("admin")
        let metrics = data.metrics.map { MetricUi(label: UiLabels.metricTitle($0.key), value: String($0.value)) }
        state.home = HomeTabUi(
            greeting: "Здравствуйте, \(data.employeeName)",
            roleTitle: UiLabels.roleTitle(role),
            metrics: metrics,
            showAdminButton: showAdmin,
            showEntryQr: state.home.showEntryQr || isTrainer,
            entryQrStaffUserId: state.home.entryQrStaffUserId,
            entryQrActive: state.home.entryQrActive,
            entryQrBlockedMessage: state.home.entryQrBlockedMessage,
            entryQrFormat: state.home.entryQrFormat,
            needNotificationsPermission: state.home.needNotificationsPermission,
            loading: true
        )
        refreshNotificationPermissionBanner()

        let homeSection: String? = switch role {
        case "ROLE_TRAINER": "bookings"
        case "ROLE_MANAGER": "tasks"
        case "ROLE_FINANCE": "clients"
        case "ROLE_SUPPORT": "app_support"
        case "ROLE_SUPER_ADMIN", "ROLE_ADMIN": "schedule"
        default: allowedSections.first { Self.homeSections.contains($0) }
        }

        if homeSection == "app_support", sectionAllowed("app_support") {
            runAsyncForTab(.home) {
                let tickets = try await self.env.withRefresh { token in
                    try await self.env.apiClient.loadSupportTickets(token: token)
                }
                guard self.state.selectedTab == .home else { return }
                let items = tickets.items.filter { $0.status == "new" }.prefix(5).map { self.ticketToCard($0) }
                self.state.home.sections = [
                    HomeSectionUi(
                        title: "Новые обращения: \(tickets.newCount)",
                        items: Array(items),
                        emptyMessage: items.isEmpty ? "Новых обращений нет" : nil
                    ),
                ]
                self.state.home.sectionTitle = "Новые обращения: \(tickets.newCount)"
                self.state.home.items = Array(items)
                self.state.home.emptyMessage = items.isEmpty ? "Новых обращений нет" : nil
                self.state.home.loading = false
            }
        } else if let homeSection, sectionAllowed(homeSection) {
            runAsyncForTab(.home) {
                if role == "ROLE_TRAINER", self.sectionAllowed("schedule") {
                    let schedule = try await self.loadScheduleCached()
                    guard self.state.selectedTab == .home else { return }
                    let sections = self.trainerHomeSections(schedule)
                    self.state.home.sections = sections
                    self.state.home.sectionTitle = sections.first?.title
                    self.state.home.items = sections.first?.items ?? []
                    self.state.home.emptyMessage = sections.first?.emptyMessage
                    self.state.home.loading = false
                } else {
                    let items = try await self.env.withRefresh { token in
                        try await self.env.apiClient.loadList(token: token, section: homeSection)
                    }
                    guard self.state.selectedTab == .home else { return }
                    let cards = items.prefix(8).map { self.feedToCard($0) }
                    self.state.home.sections = [
                        HomeSectionUi(
                            title: UiLabels.sectionTitle(homeSection),
                            items: Array(cards),
                            emptyMessage: cards.isEmpty ? "Нет данных" : nil
                        ),
                    ]
                    self.state.home.sectionTitle = UiLabels.sectionTitle(homeSection)
                    self.state.home.items = Array(cards)
                    self.state.home.emptyMessage = cards.isEmpty ? "Нет данных" : nil
                    self.state.home.loading = false
                }
            }
        } else if sectionAllowed("schedule") {
            runAsyncForTab(.home) {
                let schedule = try await self.loadScheduleCached(forceRefresh: false)
                guard self.state.selectedTab == .home else { return }
                let items = schedule.items.prefix(5).map { self.scheduleToCard($0, includeDate: true) }
                self.state.home.sections = [
                    HomeSectionUi(
                        title: "Ближайшие тренировки",
                        items: Array(items),
                        emptyMessage: items.isEmpty ? "Нет тренировок" : nil
                    ),
                ]
                self.state.home.sectionTitle = "Ближайшие тренировки"
                self.state.home.items = Array(items)
                self.state.home.emptyMessage = items.isEmpty ? "Нет тренировок" : nil
                self.state.home.loading = false
            }
        } else {
            state.home.loading = false
        }
    }

    private func trainerHomeSections(_ schedule: ScheduleData) -> [HomeSectionUi] {
        let today = todayDate()
        let tomorrow = tomorrowDate()
        let todayItems = schedule.items.filter { $0.date == today }.map { scheduleToCard($0) }
        let tomorrowItems = schedule.items.filter { $0.date == tomorrow }.map { scheduleToCard($0) }
        var sections: [HomeSectionUi] = [
            HomeSectionUi(
                title: "Записи на сегодня",
                items: todayItems,
                emptyMessage: todayItems.isEmpty ? "На сегодня записей нет — можно отдохнуть" : nil
            ),
        ]
        if !tomorrowItems.isEmpty {
            sections.append(HomeSectionUi(title: "Записи на завтра", items: tomorrowItems))
        }
        if todayItems.isEmpty && tomorrowItems.isEmpty {
            let upcoming = schedule.items
                .filter { $0.date > today }
                .prefix(5)
                .map { scheduleToCard($0, includeDate: true) }
            if !upcoming.isEmpty {
                sections.append(HomeSectionUi(title: "Ближайшие записи", items: Array(upcoming)))
            }
        }
        return sections
    }

    private func showScheduleTab() {
        state.screenTitle = "Расписание"
        state.schedule = ScheduleTabUi(loading: true)
        state.errorMessage = nil
        guard sectionAllowed("schedule") else {
            state.schedule = ScheduleTabUi(
                denied: true,
                deniedMessage: initialDataLoaded
                    ? "Раздел «Расписание» недоступен для вашей должности."
                    : "Загрузка данных...",
                loading: false
            )
            return
        }
        runAsyncForTab(.schedule) {
            let schedule = try await self.loadScheduleCached(forceRefresh: true)
            let dates = schedule.days.map(\.date)
            if self.selectedScheduleDate == nil || !(dates.contains(self.selectedScheduleDate ?? "")) {
                self.selectedScheduleDate = dates.first { $0 == self.todayDate() } ?? dates.first
            }
            self.scheduleData = schedule
            guard self.state.selectedTab == .schedule else { return }
            self.renderSchedule(schedule)
        }
    }

    func shiftSchedulePeriod(_ days: Int) {
        let formatter = Self.dayFormatter
        let current = scheduleFromDate.flatMap { formatter.date(from: $0) } ?? Date()
        guard let newFrom = Calendar.current.date(byAdding: .day, value: days, to: current) else { return }
        let today = formatter.string(from: Date())
        let newFromStr = formatter.string(from: newFrom)
        scheduleFromDate = newFromStr == today ? nil : newFromStr
        selectedScheduleDate = nil
        scheduleData = nil
        showScheduleTab()
    }

    private func ensureScheduleWindowContains(_ dateIso: String?) {
        guard let dateIso,
              let target = Self.dayFormatter.date(from: dateIso) else { return }
        let windowStart = scheduleFromDate.flatMap { Self.dayFormatter.date(from: $0) } ?? Date()
        let cal = Calendar.current
        let windowEnd = cal.date(byAdding: .day, value: 14, to: windowStart) ?? windowStart
        if target < cal.startOfDay(for: windowStart) || target >= cal.startOfDay(for: windowEnd) {
            let today = Self.dayFormatter.string(from: Date())
            scheduleFromDate = dateIso == today ? nil : dateIso
            scheduleData = nil
        }
    }

    private func renderSchedule(_ schedule: ScheduleData) {
        let today = todayDate()
        let typeFilter = selectedScheduleTypeFilter
        let days = schedule.days.map { day in
            let (weekday, dayNumber) = parseDayLabel(day.label)
            return ScheduleDayUi(
                date: day.date,
                weekdayLabel: weekday,
                dayNumber: dayNumber,
                sessionCount: day.count,
                selected: day.date == selectedScheduleDate,
                isToday: day.date == today
            )
        }
        let dayItems = schedule.items
            .filter { $0.date == selectedScheduleDate }
            .filter { $0.type != "group" }
            .filter { typeFilter == nil || $0.type == typeFilter }
        state.schedule = ScheduleTabUi(
            days: days,
            sessions: dayItems.map { scheduleToSession($0) },
            monthLabel: scheduleMonthLabel(schedule.days.map(\.date)),
            selectedTypeFilter: typeFilter,
            loading: false
        )
    }

    private func scheduleMonthLabel(_ dates: [String]) -> String {
        let formatter = Self.dayFormatter
        guard let first = dates.first.flatMap({ formatter.date(from: $0) }) else { return "" }
        let last = dates.last.flatMap { formatter.date(from: $0) } ?? first
        let firstMonth = Self.monthNames[Calendar.current.component(.month, from: first) - 1]
        let lastMonth = Self.monthNames[Calendar.current.component(.month, from: last) - 1]
        let firstYear = Calendar.current.component(.year, from: first)
        let lastYear = Calendar.current.component(.year, from: last)
        let firstMonthValue = Calendar.current.component(.month, from: first)
        let lastMonthValue = Calendar.current.component(.month, from: last)
        if firstMonthValue == lastMonthValue && firstYear == lastYear {
            return "\(firstMonth) \(firstYear)"
        }
        if firstYear == lastYear {
            return "\(firstMonth) — \(lastMonth) \(firstYear)"
        }
        return "\(firstMonth) \(firstYear) — \(lastMonth) \(lastYear)"
    }

    private func showSupportTab() {
        state.screenTitle = "Обращения"
        state.support = SupportTabUi(filters: supportFilters(), loading: true)
        state.errorMessage = nil
        guard sectionAllowed("app_support") else {
            state.support = SupportTabUi(
                denied: true,
                deniedMessage: initialDataLoaded
                    ? "Раздел «Обращения» недоступен для вашей должности."
                    : "Загрузка данных...",
                loading: false
            )
            return
        }
        runAsyncForTab(.support) {
            let tickets = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadSupportTickets(token: token, status: self.selectedSupportFilter)
            }
            let notifications = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadStaffNotifications(token: token)
            }
            self.env.sessionStore.setLastUnreadNotificationCount(notifications.unreadCount)
            guard self.state.selectedTab == .support else { return }
            let allowWrite = self.canWriteSupport()
            var actions: [ActionUi] = []
            if notifications.unreadCount > 0, allowWrite {
                actions.append(ActionUi(id: "mark_notifications_read", label: "Отметить все уведомления прочитанными"))
            }
            var ticketActions: [Int: [ActionUi]] = [:]
            for ticket in tickets.items {
                ticketActions[ticket.id] = self.buildTicketActions(ticket, allowWrite: allowWrite)
            }
            self.state.support = SupportTabUi(
                newCount: tickets.newCount,
                unreadCount: notifications.unreadCount,
                filters: self.supportFilters(),
                notifications: notifications.items.filter { !$0.isRead }.prefix(5).map {
                    ListCardUi(title: $0.title, subtitle: $0.body, meta: $0.createdAt)
                },
                tickets: tickets.items.map { self.ticketToCard($0) },
                ticketActions: ticketActions,
                actions: actions,
                loading: false
            )
        }
    }

    private func showClientsTab() {
        state.screenTitle = "Клиенты"
        let onlyActive = state.clients.onlyActiveBooking
        state.clients = ClientsTabUi(query: clientsSearchQuery, onlyActiveBooking: onlyActive, loading: true)
        state.errorMessage = nil
        guard sectionAllowed("clients") else {
            state.clients = ClientsTabUi(
                onlyActiveBooking: onlyActive,
                denied: true,
                deniedMessage: initialDataLoaded
                    ? "Раздел «Клиенты» недоступен для вашей должности."
                    : "Загрузка данных...",
                loading: false
            )
            return
        }
        loadClientsList(clientsSearchQuery)
    }

    private func loadClientsList(_ query: String) {
        let onlyActive = state.clients.onlyActiveBooking
        state.clients.query = query
        state.clients.loading = true
        state.clients.summary = ""
        state.clients.onlyActiveBooking = onlyActive
        runAsyncForTab(.clients) {
            let clients = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadClients(token: token, query: query)
            }
            guard self.state.selectedTab == .clients else { return }
            self.clientsData = clients
            self.renderClientsList()
        }
    }

    private func renderClientsList() {
        let onlyActive = state.clients.onlyActiveBooking
        let visible = clientsData
            .filter { !onlyActive || $0.hasActiveBooking }
            .sorted {
                if $0.hasActiveBooking != $1.hasActiveBooking {
                    return $0.hasActiveBooking && !$1.hasActiveBooking
                }
                return $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
            }
        state.clients = ClientsTabUi(
            query: clientsSearchQuery,
            summary: visible.isEmpty ? "" : "Найдено: \(visible.count)",
            items: visible.map { client in
                ListCardUi(
                    title: client.name.isEmpty ? "Клиент #\(client.id)" : client.name,
                    meta: "Открыть карточку",
                    badge: client.hasActiveBooking ? "Есть запись" : nil,
                    badgeColor: .success,
                    clientId: client.id
                )
            },
            onlyActiveBooking: onlyActive,
            loading: false
        )
    }

    func toggleClientsActiveFilter() {
        state.clients.onlyActiveBooking.toggle()
        renderClientsList()
    }

    private func showProfileTab() {
        let config = env.sessionStore.loadConfig() ?? env.roleConfig
        env.roleConfig = config
        let data = appData
        let sections = allowedSections.isEmpty ? (config?.appSections ?? []) : allowedSections
        let role = primaryRole()
        let adminAvailable = config?.adminActions.contains("admin.write") == true
            || ["ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_MANAGER"].contains(role)
        let showTrainerEdit = role == "ROLE_TRAINER"
            || (config?.roles ?? []).contains("ROLE_TRAINER")
        state.screenTitle = "Профиль"
        state.profile = ProfileTabUi(
            name: data?.employeeName ?? env.session?.userEmail ?? "",
            email: data?.employeeEmail ?? "",
            roleTitle: UiLabels.roleTitle(role),
            phone: state.profile.phone,
            specialization: state.profile.specialization,
            description: state.profile.description,
            photoUrl: state.profile.photoUrl,
            sections: sections
                .filter {
                    !["home", "profile", "admin"].contains($0) && !Self.hiddenAppSections.contains($0)
                }
                .reduce(into: [String]()) { result, key in
                    if !result.contains(key) { result.append(key) }
                }
                .map { key in
                    ProfileSectionUi(key: key, title: UiLabels.sectionTitle(key), hint: SectionHints.forSection(key))
                },
            adminAvailable: adminAvailable,
            showAdminButton: adminAvailable,
            showTrainerProfileEdit: showTrainerEdit,
            showClubEntryQr: showTrainerEdit,
            showRentalManage: showTrainerEdit,
            showFeedback: true,
            rentalPaidUntilLabel: formatRentalUntil(
                lastOnboarding?.activeClub?.paidUntil ?? lastOnboarding?.rentalPaidUntil,
                title: lastOnboarding?.activeClub?.title
            ) ?? state.profile.rentalPaidUntilLabel,
            paidRentalClubs: lastOnboarding?.rentalClubs.filter(\.rentalActive) ?? state.profile.paidRentalClubs,
            activeClubId: lastOnboarding?.activeClubId ?? state.profile.activeClubId,
            offerUrl: lastOnboarding?.offerUrl ?? state.profile.offerUrl,
            privacyUrl: lastOnboarding?.privacyUrl ?? state.profile.privacyUrl,
            docsUrl: lastOnboarding?.docsUrl ?? state.profile.docsUrl,
            loading: data == nil
        )
        state.errorMessage = nil

        if showTrainerEdit {
            profileLoadGeneration += 1
            let generation = profileLoadGeneration
            Task { @MainActor in
                do {
                    let onboarding = try await env.withRefresh { token in
                        try await env.apiClient.loadOnboarding(token: token)
                    }
                    lastOnboarding = onboarding
                    let trainerProfile = try await env.withRefresh { token in
                        try await env.apiClient.loadTrainerProfile(token: token)
                    }
                    guard generation == profileLoadGeneration, state.selectedTab == .profile else { return }
                    if !trainerProfile.name.isEmpty {
                        state.profile.name = trainerProfile.name
                    }
                    let national = RussianPhoneMask.normalizeNationalDigits(trainerProfile.phone)
                    state.profile.phone = national.count == 10
                        ? RussianPhoneMask.formatMask(national)
                        : trainerProfile.phone
                    state.profile.specialization = trainerProfile.specialization
                    state.profile.description = trainerProfile.description
                    state.profile.photoUrl = trainerProfile.photoUrl
                    applyProfileRentalState(onboarding)
                } catch {
                    // Trainer card is optional; failures must not block the tab.
                }
            }
        } else if lastOnboarding == nil {
            Task { await refreshEntryQrState() }
        } else if let onboarding = lastOnboarding {
            applyProfileRentalState(onboarding)
        }

        let extraSections = sections.filter {
            !["home", "profile", "schedule", "dashboard", "admin", "clients", "app_support"].contains($0)
                && !Self.hiddenAppSections.contains($0)
        }
        if let section = extraSections.first {
            runAsyncForTab(.profile) {
                let items = try await self.env.withRefresh { token in
                    try await self.env.apiClient.loadList(token: token, section: section)
                }
                guard self.state.selectedTab == .profile else { return }
                self.state.profile.sectionTitle = UiLabels.sectionTitle(section)
                self.state.profile.items = items.prefix(10).map { self.feedToCard($0) }
                self.state.profile.loading = false
            }
        } else if data != nil {
            state.profile.loading = false
        }
    }

    // MARK: - Helpers

    private func sectionAllowed(_ section: String) -> Bool {
        if allowedSections.contains(section) { return true }
        let config = env.sessionStore.loadConfig() ?? env.roleConfig
        return config?.appSections.contains(section) == true || config?.adminSections.contains(section) == true
    }

    private func supportFilters() -> [DayChipUi] {
        [
            DayChipUi(date: "", label: "Все", count: -1, selected: selectedSupportFilter == nil),
            DayChipUi(date: "new", label: "Новые", count: -1, selected: selectedSupportFilter == "new"),
            DayChipUi(date: "in_progress", label: "В работе", count: -1, selected: selectedSupportFilter == "in_progress"),
            DayChipUi(date: "done", label: "Закрыто", count: -1, selected: selectedSupportFilter == "done"),
        ]
    }

    private func buildTicketActions(_ ticket: SupportTicketItem, allowWrite: Bool) -> [ActionUi] {
        var actions: [ActionUi] = []
        if ticket.clientId != nil, sectionAllowed("clients") {
            actions.append(ActionUi(id: "ticket_client:\(ticket.clientId!)", label: "Карточка клиента"))
        }
        if allowWrite, ticket.status != "done" {
            if ticket.status == "new" {
                actions.append(ActionUi(id: "ticket_status:\(ticket.id):in_progress", label: "Взять в работу"))
            }
            actions.append(ActionUi(id: "ticket_status:\(ticket.id):done", label: "Закрыть обращение"))
        }
        return actions
    }

    private func ticketToCard(_ ticket: SupportTicketItem) -> ListCardUi {
        let client = ticket.clientName.isEmpty
            ? (ticket.contactEmail.isEmpty ? "Клиент не указан" : ticket.contactEmail)
            : ticket.clientName
        let contact = [ticket.contactEmail, ticket.clientPhone].filter { !$0.isEmpty }.joined(separator: " · ")
        var meta = "\(UiLabels.ticketCategory(ticket.category)) · \(ticket.createdAt)"
        if !contact.isEmpty { meta += "\n\(contact)" }
        return ListCardUi(
            title: ticket.subject,
            subtitle: "Клиент: \(client)\n\(ticket.message)",
            meta: meta,
            badge: UiLabels.ticketStatus(ticket.status),
            badgeColor: ticketBadgeColor(ticket.status),
            clientId: ticket.clientId,
            ticketId: ticket.id,
            refType: "ticket"
        )
    }

    private func ticketBadgeColor(_ status: String) -> BadgeColor {
        switch status {
        case "new": return .warning
        case "in_progress": return .primary
        case "done": return .success
        default: return .neutral
        }
    }

    private func scheduleToCard(_ item: ScheduleItem, includeDate: Bool = false) -> ListCardUi {
        let clients = item.clientNames.isEmpty
            ? (item.participants.isEmpty ? "нет записей" : item.participants)
            : item.clientNames.joined(separator: ", ")
        let datePrefix: String = {
            guard includeDate else { return "" }
            let label = item.dayLabel.isEmpty ? item.date : item.dayLabel
            return "\(label) · "
        }()
        return ListCardUi(
            title: "\(datePrefix)\(item.startTime)–\(item.endTime)  \(item.title)",
            subtitle: "Клиенты: \(clients)",
            meta: "\(UiLabels.trainingType(item.type)) · \(item.trainer) · \(item.room)",
            trainingId: item.id,
            trainingDate: item.date
        )
    }

    private func scheduleToSession(_ item: ScheduleItem) -> ScheduleSessionUi {
        let (booked, max) = parseParticipants(item.participants)
        return ScheduleSessionUi(
            trainingId: item.id,
            date: item.date,
            title: item.title,
            type: item.type,
            typeLabel: UiLabels.trainingType(item.type),
            startTime: item.startTime,
            endTime: item.endTime,
            durationMinutes: durationMinutes(item.startTime, item.endTime),
            trainer: item.trainer,
            room: item.room,
            bookedCount: item.currentParticipants ?? booked,
            maxParticipants: item.maxParticipants ?? max,
            clientNames: item.clientNames,
            bookings: item.bookings.map { b in
                let cid: Int? = {
                    guard let raw = b.clientId, !raw.isEmpty else { return nil }
                    if raw.hasPrefix("user-") {
                        return Int(raw.dropFirst(5))
                    }
                    return Int(raw)
                }()
                return ScheduleBookingUi(id: b.id, clientName: b.clientName, clientId: cid)
            }
        )
    }

    func openAssignDialog(for session: ScheduleSessionUi) {
        guard let trainingId = session.trainingId, !trainingId.isEmpty else { return }
        assignDialogSession = session
        state.assignDialog = AssignClientDialogUi(
            trainingId: trainingId,
            sessionTitle: "\(session.startTime) \(session.title)",
            booked: session.bookings.map { b in
                ListCardUi(title: b.clientName, meta: b.id, clientId: b.clientId)
            }
        )
        searchAssignClients()
    }

    func dismissAssignDialog() {
        state.assignDialog = nil
        assignDialogSession = nil
    }

    func onAssignQueryChange(_ query: String) {
        state.assignDialog?.query = query
    }

    func searchAssignClients() {
        guard var dialog = state.assignDialog else { return }
        dialog.loading = true
        dialog.errorMessage = nil
        state.assignDialog = dialog
        let query = dialog.query
        Task { @MainActor in
            do {
                let clients = try await env.withRefresh { token in
                    try await env.apiClient.loadClients(token: token, query: query)
                }
                guard state.assignDialog?.trainingId == dialog.trainingId else { return }
                state.assignDialog?.clients = clients.map { c in
                    ListCardUi(
                        title: c.name.isEmpty ? "Клиент #\(c.id)" : c.name,
                        subtitle: [c.email, c.phone].filter { !$0.isEmpty }.joined(separator: " · "),
                        clientId: c.id
                    )
                }
                state.assignDialog?.loading = false
            } catch {
                state.assignDialog?.loading = false
                state.assignDialog?.errorMessage = UserFacingError.message(error)
            }
        }
    }

    func bookAssignClient(_ clientId: Int) {
        guard let dialog = state.assignDialog else { return }
        let trainingId = dialog.trainingId
        state.assignDialog?.loading = true
        Task { @MainActor in
            do {
                try await env.withRefresh { token in
                    try await env.apiClient.bookClientOnTraining(
                        token: token,
                        trainingId: trainingId,
                        clientId: clientId
                    )
                }
                scheduleData = nil
                let schedule = try await loadScheduleCached(forceRefresh: true)
                if state.selectedTab == .schedule {
                    renderSchedule(schedule)
                }
                if let item = schedule.items.first(where: { $0.id == trainingId }) {
                    openAssignDialog(for: scheduleToSession(item))
                } else {
                    state.assignDialog = nil
                    assignDialogSession = nil
                }
                state.statusMessage = "Клиент записан"
            } catch {
                state.assignDialog?.loading = false
                state.assignDialog?.errorMessage = UserFacingError.message(error)
            }
        }
    }

    func cancelAssignBooking(_ bookingId: String) {
        guard state.assignDialog != nil else { return }
        state.assignDialog?.loading = true
        Task { @MainActor in
            do {
                let trainingRemoved = try await env.withRefresh { token in
                    try await env.apiClient.cancelStaffBooking(token: token, bookingId: bookingId)
                }
                scheduleData = nil
                state.assignDialog = nil
                assignDialogSession = nil
                if state.selectedTab == .schedule {
                    showScheduleTab()
                }
                state.statusMessage = trainingRemoved
                    ? "Запись снята, занятие убрано из расписания"
                    : "Запись снята"
            } catch {
                state.assignDialog?.loading = false
                state.assignDialog?.errorMessage = UserFacingError.message(error)
            }
        }
    }

    func updateTrainingSession(
        trainingId: String,
        name: String,
        date: Date,
        startTime: Date,
        durationMinutes: Int,
        room: String
    ) async throws {
        let cal = Calendar.current
        let day = cal.dateComponents([.year, .month, .day], from: date)
        let time = cal.dateComponents([.hour, .minute], from: startTime)
        guard let y = day.year, let m = day.month, let d = day.day,
              let hour = time.hour, let minute = time.minute else {
            throw StaffApiError.parseFailed("Некорректная дата")
        }
        if durationMinutes <= 0 {
            throw StaffApiError.parseFailed("Выберите длительность занятия")
        }
        var startComponents = DateComponents()
        startComponents.year = y
        startComponents.month = m
        startComponents.day = d
        startComponents.hour = hour
        startComponents.minute = minute
        guard let start = cal.date(from: startComponents),
              let end = cal.date(byAdding: .minute, value: durationMinutes, to: start) else {
            throw StaffApiError.parseFailed("Некорректная дата")
        }
        let endParts = cal.dateComponents([.year, .month, .day, .hour, .minute], from: end)
        if endParts.year != y || endParts.month != m || endParts.day != d || !endParts.isAfterSameDay(hour: hour, minute: minute) {
            throw StaffApiError.parseFailed("Занятие должно заканчиваться в тот же день. Уменьшите длительность или измените время начала.")
        }
        let dateLabel = String(format: "%04d-%02d-%02d", y, m, d)
        let startTimeLabel = String(format: "%02d:%02d", hour, minute)
        let endTimeLabel = String(format: "%02d:%02d", endParts.hour ?? 0, endParts.minute ?? 0)
        let roomTrimmed = room.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameTrimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = nameTrimmed.isEmpty ? "Персональная тренировка" : nameTrimmed
        let updated = try await env.withRefresh { token in
            try await env.apiClient.updateTraining(
                token: token,
                trainingId: trainingId,
                name: resolvedName,
                startAtIso: "\(dateLabel)T\(startTimeLabel):00",
                endAtIso: "\(dateLabel)T\(endTimeLabel):00",
                room: roomTrimmed.isEmpty ? nil : roomTrimmed
            )
        }
        scheduleData = nil
        selectedScheduleDate = updated.date.isEmpty ? dateLabel : updated.date
        ensureScheduleWindowContains(selectedScheduleDate)
        if state.selectedTab == .schedule {
            showScheduleTab()
        }
        state.statusMessage = "Занятие обновлено"
    }

    private func parseDayLabel(_ label: String) -> (String, String) {
        let parts = label.trimmingCharacters(in: .whitespaces).split(separator: " ").map(String.init)
        if parts.count >= 2 { return (parts[0], parts[1]) }
        if parts.count == 1 { return ("", parts[0]) }
        return ("", "")
    }

    private func parseParticipants(_ participants: String) -> (Int?, Int?) {
        let pattern = #"(\d+)\s*/\s*(\d+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: participants, range: NSRange(participants.startIndex..., in: participants)),
              match.numberOfRanges >= 3,
              let r1 = Range(match.range(at: 1), in: participants),
              let r2 = Range(match.range(at: 2), in: participants),
              let a = Int(participants[r1]),
              let b = Int(participants[r2]) else {
            return (nil, nil)
        }
        return (a, b)
    }

    private func durationMinutes(_ start: String, _ end: String) -> Int {
        func toMinutes(_ value: String) -> Int? {
            let parts = value.split(separator: ":")
            guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
            return h * 60 + m
        }
        guard let startMinutes = toMinutes(start), let endMinutes = toMinutes(end) else { return 60 }
        let diff = endMinutes - startMinutes
        return diff > 0 ? diff : 60
    }

    private func feedToCard(_ item: FeedListItem) -> ListCardUi {
        ListCardUi(
            title: item.title,
            subtitle: item.subtitle,
            meta: item.meta,
            clientId: item.refType == "client" ? item.id : nil,
            ticketId: item.refType == "ticket" ? item.id : nil,
            refType: item.refType,
            feedId: item.id
        )
    }

    private func updateTicketStatus(_ ticketId: Int, status: String) {
        runAsyncForTab(.support) {
            _ = try await self.env.withRefresh { token in
                try await self.env.apiClient.updateSupportTicketStatus(token: token, ticketId: ticketId, status: status)
            }
            self.showSupportTab()
        }
    }

    private func canWriteSupport() -> Bool {
        let actions = env.roleConfig?.adminActions ?? []
        return actions.contains("admin.write") || actions.contains("support.write")
    }

    private func primaryRole() -> String {
        let roles = Array(Set((env.roleConfig?.roles ?? []) + (appData?.roles ?? [])))
        let priority = [
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_TRAINER", "ROLE_MANAGER",
            "ROLE_SUPPORT", "ROLE_FINANCE", "ROLE_VIEWER",
        ]
        return priority.first { roles.contains($0) }
            ?? roles.first { $0 != "ROLE_STAFF" }
            ?? "ROLE_VIEWER"
    }

    private func todayDate() -> String {
        Self.dayFormatter.string(from: Date())
    }

    private func tomorrowDate() -> String {
        let cal = Calendar.current
        let day = cal.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        return Self.dayFormatter.string(from: day)
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private func loadScheduleCached(forceRefresh: Bool = false) async throws -> ScheduleData {
        if !forceRefresh, let scheduleData { return scheduleData }
        let from = scheduleFromDate
        let data = try await env.withRefresh { token in
            try await env.apiClient.loadSchedule(token: token, from: from)
        }
        scheduleData = data
        return data
    }

    private func openExternalUrl(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let url = URL(string: trimmed) else { return }
        UIApplication.shared.open(url)
    }

    private func requestOrOpenNotificationSettings() {
        Task { @MainActor in
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            switch settings.authorizationStatus {
            case .notDetermined:
                _ = try? await UNUserNotificationCenter.current()
                    .requestAuthorization(options: [.alert, .sound, .badge])
                refreshNotificationPermissionBanner()
            case .denied:
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    await UIApplication.shared.open(url)
                }
                refreshNotificationPermissionBanner()
            default:
                refreshNotificationPermissionBanner()
            }
        }
    }

    private func refreshNotificationPermissionBanner() {
        Task { @MainActor in
            let settings = await UNUserNotificationCenter.current().notificationSettings()
            let enabled = settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional
                || settings.authorizationStatus == .ephemeral
            state.home.needNotificationsPermission = !enabled
        }
    }

    private func runAsync(_ message: String, action: @escaping () async throws -> Void) {
        let generation = loadGeneration + 1
        loadGeneration = generation
        Task {
            do {
                try await action()
                if generation == loadGeneration { state.errorMessage = nil }
            } catch {
                if generation == loadGeneration {
                    state.errorMessage = UserFacingError.message(error)
                }
            }
        }
    }

    private func runAsyncForTab(_ tab: WorkTab, action: @escaping () async throws -> Void) {
        let generation = loadGeneration + 1
        loadGeneration = generation
        Task {
            do {
                try await action()
                if generation == loadGeneration, state.selectedTab == tab {
                    state.errorMessage = nil
                }
            } catch {
                if generation == loadGeneration, state.selectedTab == tab {
                    state.errorMessage = UserFacingError.message(error)
                    if tab == .schedule { scheduleData = nil }
                }
            }
        }
    }
}

private extension DateComponents {
    func isAfterSameDay(hour: Int, minute: Int) -> Bool {
        let eh = self.hour ?? 0
        let em = self.minute ?? 0
        return eh > hour || (eh == hour && em > minute)
    }
}
