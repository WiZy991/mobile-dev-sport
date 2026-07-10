import Foundation
import Observation

@Observable
@MainActor
final class AdminHubController {
    var state = AdminHubUi()
    private let env: AppEnvironment
    private var adminMenu: [String: String] = [:]

    var onLogout: (() -> Void)?
    var onSectionSelected: ((String) -> Void)?

    init(env: AppEnvironment) {
        self.env = env
    }

    func loadData() {
        state.loading = true
        state.error = nil
        Task {
            do {
                if env.roleConfig?.adminSections.isEmpty ?? true {
                    throw StaffApiError.http(status: 403, detail: "Нет доступа к админке")
                }
                let data = try await env.withRefresh { token in
                    try await env.apiClient.loadAdminData(token: token)
                }
                adminMenu = data.adminMenu
                state = AdminHubUi(
                    canWrite: data.canWrite,
                    metrics: data.widgets.map { MetricUi(label: UiLabels.metricTitle($0.key), value: String($0.value)) },
                    sections: data.adminSections.map { section in
                        AdminSectionRowUi(
                            key: section,
                            title: adminMenu[section] ?? UiLabels.sectionTitle(section),
                            hint: SectionHints.forSection(section)
                        )
                    },
                    loading: false
                )
            } catch {
                state = AdminHubUi(loading: false, error: UserFacingError.message(error))
            }
        }
    }

    func logout() {
        env.logout()
        onLogout?()
    }
}

@Observable
@MainActor
final class AdminSectionController {
    var state = AdminSectionUi()
    let section: String
    private let env: AppEnvironment

    var onShortcut: ((WorkTab) -> Void)?
    var onOpenClient: ((Int) -> Void)?

    init(env: AppEnvironment, section: String) {
        self.env = env
        self.section = section
        state.title = UiLabels.sectionTitle(section)
    }

    func loadSection() {
        guard !section.isEmpty else {
            state = AdminSectionUi(loading: false, error: "Раздел не указан")
            return
        }
        state.loading = true
        state.error = nil
        Task {
            do {
                let cards = try await env.withRefresh { token in
                    try await env.apiClient.loadSectionData(token: token, mode: "admin", section: section)
                }
                let items = try await env.withRefresh { token in
                    try await env.apiClient.loadList(token: token, section: section)
                }
                state = AdminSectionUi(
                    title: UiLabels.sectionTitle(section),
                    metrics: cards.cards.map { MetricUi(label: UiLabels.metricTitle($0.key), value: String($0.value)) },
                    items: items.map { feedToCard($0) },
                    shortcuts: sectionShortcuts(),
                    summary: items.isEmpty ? "Записей в разделе пока нет" : "Записей: \(items.count)",
                    loading: false
                )
            } catch {
                state = AdminSectionUi(title: UiLabels.sectionTitle(section), loading: false, error: UserFacingError.message(error))
            }
        }
    }

    func handleAction(_ actionId: String) {
        switch actionId {
        case "shortcut:schedule": onShortcut?(.schedule)
        case "shortcut:clients": onShortcut?(.clients)
        case "shortcut:support": onShortcut?(.support)
        default: break
        }
    }

    func handleItemClick(_ item: ListCardUi) {
        switch item.refType {
        case "client":
            let clientId = item.clientId ?? item.feedId
            if let clientId { onOpenClient?(clientId) }
        case "ticket":
            onShortcut?(.support)
        default:
            break
        }
    }

    private func sectionShortcuts() -> [ActionUi] {
        switch section {
        case "schedule": return [ActionUi(id: "shortcut:schedule", label: "Открыть календарь расписания")]
        case "clients": return [ActionUi(id: "shortcut:clients", label: "Открыть поиск клиентов")]
        case "app_support": return [ActionUi(id: "shortcut:support", label: "Открыть обращения с фильтрами")]
        default: return []
        }
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
}

@Observable
@MainActor
final class ClientDetailController {
    var state = ClientDetailUi()
    private let env: AppEnvironment
    private let clientId: Int
    private(set) var phone: String?

    init(env: AppEnvironment, clientId: Int) {
        self.env = env
        self.clientId = clientId
    }

    func loadClient() {
        guard clientId > 0 else {
            state = ClientDetailUi(loading: false, error: "Клиент не найден")
            return
        }
        state.loading = true
        Task {
            do {
                let detail = try await env.withRefresh { token in
                    try await env.apiClient.loadClientDetail(token: token, clientId: clientId)
                }
                renderClient(detail)
            } catch {
                state = ClientDetailUi(loading: false, error: UserFacingError.message(error))
            }
        }
    }

    private func renderClient(_ client: ClientDetail) {
        phone = client.phone
        let sub = client.subscription
        var subMeta = ""
        if let sub {
            subMeta = sub.status
            if let end = sub.endDate, !end.isEmpty { subMeta += " · до \(end)" }
            if sub.visitsTotal > 0 { subMeta += "\nВизиты: \(sub.visitsUsed)/\(sub.visitsTotal)" }
        }
        state = ClientDetailUi(
            title: client.name,
            name: client.name,
            email: client.email,
            phone: client.phone,
            bonusPoints: client.bonusPoints,
            isBlocked: client.isBlocked,
            subscriptionTitle: sub?.plan ?? "",
            subscriptionMeta: subMeta,
            bookings: client.recentBookings.map { ListCardUi(title: $0.title, meta: $0.meta) },
            tickets: client.recentTickets.map { ListCardUi(title: $0.title, meta: $0.meta) },
            loading: false,
            showCallButton: !client.phone.isEmpty
        )
    }
}
