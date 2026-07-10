import SwiftUI

struct WorkView: View {
    @Bindable var controller: WorkController
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        VStack(spacing: 0) {
            tabContent
            if let error = controller.state.errorMessage {
                StaffErrorState(message: error, onRetry: { controller.handleAction("retry") })
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
            }
            bottomBar
        }
        .background(StaffColors.background)
        .navigationTitle(controller.state.screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    controller.logout()
                } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .foregroundStyle(.white)
                }
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.onAppear() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { controller.onResume() }
        }
    }

    @ViewBuilder
    private var tabContent: some View {
        switch controller.state.selectedTab {
        case .home:
            homeTab
        case .schedule:
            StaffScheduleTabContent(
                schedule: controller.state.schedule,
                onDaySelected: { controller.onScheduleDaySelected($0) },
                onTypeFilterSelected: { controller.onScheduleTypeFilterSelected($0) }
            )
        case .clients:
            clientsTab
        case .support:
            supportTab
        case .profile:
            profileTab
        }
    }

    private var homeTab: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if controller.state.home.loading && controller.state.home.greeting.isEmpty {
                    StaffLoadingState()
                } else {
                    StaffHeroCard(
                        title: controller.state.home.greeting.isEmpty ? "Добро пожаловать" : controller.state.home.greeting,
                        subtitle: controller.state.home.roleTitle
                    )
                    if !controller.state.home.metrics.isEmpty {
                        StaffMetricsRow(metrics: controller.state.home.metrics)
                    }
                    if controller.state.home.showAdminButton {
                        StaffPrimaryButton(text: "Открыть админку") {
                            controller.handleAction("open_admin")
                        }
                    }
                    if let title = controller.state.home.sectionTitle {
                        StaffSectionTitle(title: title)
                    }
                    if controller.state.home.loading {
                        StaffLoadingState()
                    } else if controller.state.home.items.isEmpty, let empty = controller.state.home.emptyMessage {
                        StaffEmptyState(message: empty)
                    } else {
                        ForEach(controller.state.home.items) { item in
                            StaffListCard(item: item, onClick: item.isClickable ? { controller.handleListCardClick(item) } : nil)
                        }
                    }
                    if !controller.state.home.actions.isEmpty {
                        StaffActionButtons(actions: controller.state.home.actions, onAction: controller.handleAction)
                    }
                }
            }
            .padding(16)
        }
    }

    private var clientsTab: some View {
        VStack(spacing: 0) {
            if controller.state.clients.denied {
                StaffEmptyState(message: controller.state.clients.deniedMessage)
                    .padding(16)
            } else {
                StaffSearchBar(
                    query: controller.state.clients.query,
                    onQueryChange: { controller.onClientSearchQueryChange($0) },
                    onSearch: { controller.onClientSearch() }
                )
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if controller.state.clients.loading {
                            StaffLoadingState(message: "Поиск клиентов...")
                        } else if controller.state.clients.items.isEmpty {
                            StaffEmptyState(message: "Клиенты не найдены")
                        } else {
                            if !controller.state.clients.summary.isEmpty {
                                StaffInfoBanner(text: controller.state.clients.summary)
                            }
                            ForEach(controller.state.clients.items) { item in
                                StaffListCard(item: item) { controller.handleListCardClick(item) }
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
    }

    private var supportTab: some View {
        VStack(spacing: 0) {
            if controller.state.support.denied {
                StaffEmptyState(message: controller.state.support.deniedMessage)
                    .padding(16)
            } else {
                if !controller.state.support.filters.isEmpty {
                    StaffChipRow(chips: controller.state.support.filters) { controller.onSupportFilterSelected($0) }
                }
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if controller.state.support.loading {
                            StaffLoadingState(message: "Загрузка обращений...")
                        } else {
                            StaffInfoBanner(text: supportBannerText)
                            if !controller.state.support.actions.isEmpty {
                                StaffActionButtons(actions: controller.state.support.actions, onAction: controller.handleAction)
                            }
                            if !controller.state.support.notifications.isEmpty {
                                StaffSectionTitle(title: "Уведомления")
                                ForEach(controller.state.support.notifications) { item in
                                    StaffListCard(item: item)
                                }
                            }
                            StaffSectionTitle(title: "Обращения")
                            if controller.state.support.tickets.isEmpty {
                                StaffEmptyState(message: "Обращений по фильтру нет")
                            } else {
                                ForEach(controller.state.support.tickets) { ticket in
                                    VStack(spacing: 8) {
                                        StaffListCard(item: ticket, onClick: ticket.isClickable ? { controller.handleListCardClick(ticket) } : nil)
                                        if let ticketId = ticket.ticketId, let actions = controller.state.support.ticketActions[ticketId] {
                                            StaffActionButtons(actions: actions, onAction: controller.handleAction)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(16)
                }
            }
        }
    }

    private var supportBannerText: String {
        var text = "Новых обращений: \(controller.state.support.newCount)"
        if controller.state.support.unreadCount > 0 {
            text += " · Непрочитанных: \(controller.state.support.unreadCount)"
        }
        return text
    }

    private var profileTab: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                StaffHeroCard(
                    title: controller.state.profile.name.isEmpty ? "Сотрудник" : controller.state.profile.name,
                    subtitle: controller.state.profile.roleTitle
                )
                if !controller.state.profile.email.isEmpty {
                    StaffInfoBanner(text: controller.state.profile.email, color: StaffColors.onSurfaceVariant)
                }
                if controller.state.profile.showAdminButton {
                    StaffPrimaryButton(text: "Открыть админку") {
                        controller.handleAction("open_admin")
                    }
                }
                if !controller.state.profile.sections.isEmpty {
                    StaffMenuCard(
                        title: "Доступные разделы",
                        items: controller.state.profile.sections.map { section in
                            (SectionIcons.forSection(section.key), section.title, section.hint)
                        },
                        onItemClick: { index in
                            controller.handleProfileSectionClick(controller.state.profile.sections[index].key)
                        }
                    )
                }
                StaffInfoBanner(
                    text: controller.state.profile.adminAvailable
                        ? "Админка CRM доступна"
                        : "Админка для вашей должности недоступна"
                )
                if let title = controller.state.profile.sectionTitle {
                    StaffSectionTitle(title: title)
                }
                if controller.state.profile.loading {
                    StaffLoadingState()
                } else {
                    ForEach(controller.state.profile.items) { item in
                        StaffListCard(item: item, onClick: item.isClickable ? { controller.handleListCardClick(item) } : nil)
                    }
                }
                Spacer().frame(height: 16)
            }
            .padding(16)
        }
    }

    private var bottomBar: some View {
        HStack {
            ForEach(navItems, id: \.tab) { item in
                Button {
                    controller.selectTab(item.tab)
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: controller.state.selectedTab == item.tab ? item.selectedIcon : item.icon)
                            .font(.system(size: 20))
                        Text(item.label)
                            .font(.caption2)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(controller.state.selectedTab == item.tab ? StaffColors.primary : StaffColors.onSurfaceVariant)
                    .padding(.vertical, 8)
                    .background(
                        controller.state.selectedTab == item.tab
                            ? StaffColors.primary.opacity(0.12)
                            : Color.clear
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 4)
        .background(StaffColors.surface)
        .shadow(color: .black.opacity(0.08), radius: 4, y: -2)
    }

    private struct NavItem {
        let tab: WorkTab
        let label: String
        let icon: String
        let selectedIcon: String
    }

    private var navItems: [NavItem] {
        var items: [NavItem] = [
            NavItem(tab: .home, label: "Главная", icon: "house", selectedIcon: "house.fill"),
        ]
        if controller.state.showScheduleNav {
            items.append(NavItem(tab: .schedule, label: "Расписание", icon: "calendar", selectedIcon: "calendar"))
        }
        if controller.state.showClientsNav {
            items.append(NavItem(tab: .clients, label: "Клиенты", icon: "person.2", selectedIcon: "person.2.fill"))
        }
        items.append(NavItem(tab: .profile, label: "Профиль", icon: "person", selectedIcon: "person.fill"))
        if controller.state.showSupportNav {
            items.append(NavItem(tab: .support, label: "Обращения", icon: "headphones", selectedIcon: "headphones"))
        }
        return items
    }
}
