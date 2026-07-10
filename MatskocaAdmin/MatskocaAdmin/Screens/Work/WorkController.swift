import Foundation
import Observation

@Observable
@MainActor
final class WorkController {
    var state = WorkUiState()
    private let env: AppEnvironment

    private var appData: StaffAppData?
    private var allowedSections: [String] = []
    private var scheduleData: ScheduleData?
    private var selectedScheduleDate: String?
    private var selectedScheduleTypeFilter: String?
    private var selectedSupportFilter: String?
    private var clientsSearchQuery = ""
    private var loadGeneration = 0
    private var initialDataLoaded = false

    private static let homeSections: Set<String> = [
        "bookings", "clients", "tasks", "subscriptions", "schedule", "app_support",
    ]

    var onOpenAdmin: (() -> Void)?
    var onOpenClient: ((Int) -> Void)?
    var onOpenAdminSection: ((String) -> Void)?

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
            if actionId.hasPrefix("ticket_status:") {
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
            self.refreshActiveTab()
        }
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
        state.home = HomeTabUi(loading: appData == nil)
        guard let data = appData else { return }

        let role = primaryRole()
        let showAdmin = !(env.roleConfig?.adminSections.isEmpty ?? true) || allowedSections.contains("admin")
        let metrics = data.metrics.map { MetricUi(label: UiLabels.metricTitle($0.key), value: String($0.value)) }
        state.home = HomeTabUi(
            greeting: "Здравствуйте, \(data.employeeName)",
            roleTitle: UiLabels.roleTitle(role),
            metrics: metrics,
            showAdminButton: showAdmin,
            loading: true
        )

        let homeSection: String? = switch role {
        case "ROLE_TRAINER": "bookings"
        case "ROLE_MANAGER": "tasks"
        case "ROLE_FINANCE": "subscriptions"
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
                    let today = self.todayDate()
                    let filtered = schedule.items.filter { $0.date == today }
                    let items = (filtered.isEmpty ? Array(schedule.items.prefix(5)) : filtered).map { self.scheduleToCard($0) }
                    self.state.home.sectionTitle = "Ваши тренировки сегодня"
                    self.state.home.items = items
                    self.state.home.emptyMessage = items.isEmpty ? "Нет тренировок" : nil
                    self.state.home.loading = false
                } else {
                    let items = try await self.env.withRefresh { token in
                        try await self.env.apiClient.loadList(token: token, section: homeSection)
                    }
                    guard self.state.selectedTab == .home else { return }
                    self.state.home.sectionTitle = UiLabels.sectionTitle(homeSection)
                    self.state.home.items = items.prefix(8).map { self.feedToCard($0) }
                    self.state.home.emptyMessage = items.isEmpty ? "Нет данных" : nil
                    self.state.home.loading = false
                }
            }
        } else if sectionAllowed("schedule") {
            runAsyncForTab(.home) {
                let schedule = try await self.loadScheduleCached(forceRefresh: false)
                guard self.state.selectedTab == .home else { return }
                let items = schedule.items.prefix(5).map { self.scheduleToCard($0) }
                self.state.home.sectionTitle = "Ближайшие тренировки"
                self.state.home.items = items
                self.state.home.emptyMessage = items.isEmpty ? "Нет тренировок" : nil
                self.state.home.loading = false
            }
        } else {
            state.home.loading = false
        }
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
            if self.selectedScheduleDate == nil {
                self.selectedScheduleDate = schedule.days.first { $0.date == self.todayDate() }?.date
                    ?? schedule.days.first?.date
            }
            self.scheduleData = schedule
            guard self.state.selectedTab == .schedule else { return }
            self.renderSchedule(schedule)
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
            .filter { typeFilter == nil || $0.type == typeFilter }
        state.schedule = ScheduleTabUi(
            days: days,
            sessions: dayItems.map { scheduleToSession($0) },
            selectedTypeFilter: typeFilter,
            loading: false
        )
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
        state.clients = ClientsTabUi(query: clientsSearchQuery, loading: true)
        state.errorMessage = nil
        guard sectionAllowed("clients") else {
            state.clients = ClientsTabUi(
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
        state.clients.query = query
        state.clients.loading = true
        state.clients.summary = ""
        runAsyncForTab(.clients) {
            let clients = try await self.env.withRefresh { token in
                try await self.env.apiClient.loadClients(token: token, query: query)
            }
            guard self.state.selectedTab == .clients else { return }
            self.state.clients = ClientsTabUi(
                query: query,
                summary: clients.isEmpty ? "" : "Найдено: \(clients.count)",
                items: clients.map { client in
                    ListCardUi(
                        title: client.name.isEmpty ? "Клиент #\(client.id)" : client.name,
                        subtitle: [client.email, client.phone].filter { !$0.isEmpty }.joined(separator: "\n"),
                        meta: "Открыть карточку",
                        clientId: client.id
                    )
                },
                loading: false
            )
        }
    }

    private func showProfileTab() {
        let config = env.sessionStore.loadConfig() ?? env.roleConfig
        env.roleConfig = config
        let data = appData
        let sections = allowedSections.isEmpty ? (config?.appSections ?? []) : allowedSections
        let adminAvailable = !(config?.adminSections.isEmpty ?? true) || sections.contains("admin")
        state.screenTitle = "Профиль"
        state.profile = ProfileTabUi(
            name: data?.employeeName ?? env.session?.userEmail ?? "",
            email: data?.employeeEmail ?? "",
            roleTitle: UiLabels.roleTitle(primaryRole()),
            sections: sections
                .filter { !["home", "profile", "admin"].contains($0) }
                .map { key in
                    ProfileSectionUi(key: key, title: UiLabels.sectionTitle(key), hint: SectionHints.forSection(key))
                },
            adminAvailable: adminAvailable,
            showAdminButton: adminAvailable,
            loading: data == nil
        )
        state.errorMessage = nil

        let extraSections = sections.filter {
            !["home", "profile", "schedule", "dashboard", "admin", "clients", "app_support"].contains($0)
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

    private func scheduleToCard(_ item: ScheduleItem) -> ListCardUi {
        let clients = item.clientNames.isEmpty
            ? (item.participants.isEmpty ? "нет записей" : item.participants)
            : item.clientNames.joined(separator: ", ")
        return ListCardUi(
            title: "\(item.startTime)–\(item.endTime)  \(item.title)",
            subtitle: "Клиенты: \(clients)",
            meta: "\(UiLabels.trainingType(item.type)) · \(item.trainer) · \(item.room)"
        )
    }

    private func scheduleToSession(_ item: ScheduleItem) -> ScheduleSessionUi {
        let (booked, max) = parseParticipants(item.participants)
        return ScheduleSessionUi(
            title: item.title,
            type: item.type,
            typeLabel: UiLabels.trainingType(item.type),
            startTime: item.startTime,
            endTime: item.endTime,
            durationMinutes: durationMinutes(item.startTime, item.endTime),
            trainer: item.trainer,
            room: item.room,
            bookedCount: booked,
            maxParticipants: max,
            clientNames: item.clientNames
        )
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
        let roles = env.roleConfig?.roles ?? []
        let priority = [
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_TRAINER", "ROLE_MANAGER",
            "ROLE_SUPPORT", "ROLE_FINANCE", "ROLE_VIEWER",
        ]
        return priority.first { roles.contains($0) } ?? roles.first ?? "ROLE_VIEWER"
    }

    private func todayDate() -> String {
        let cal = Calendar.current
        let y = cal.component(.year, from: Date())
        let m = cal.component(.month, from: Date())
        let d = cal.component(.day, from: Date())
        return String(format: "%04d-%02d-%02d", y, m, d)
    }

    private func loadScheduleCached(forceRefresh: Bool = false) async throws -> ScheduleData {
        if !forceRefresh, let scheduleData { return scheduleData }
        let data = try await env.withRefresh { token in
            try await env.apiClient.loadSchedule(token: token)
        }
        scheduleData = data
        return data
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
