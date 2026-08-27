import SwiftUI

struct WorkView: View {
    @Bindable var controller: WorkController
    @Environment(\.scenePhase) private var scenePhase
    @State private var showCreateSession = false

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
                HStack(spacing: 12) {
                    if controller.state.selectedTab == .schedule, !controller.state.schedule.denied {
                        Button {
                            showCreateSession = true
                        } label: {
                            Image(systemName: "plus")
                                .foregroundStyle(.white)
                        }
                    }
                    Button {
                        controller.logout()
                    } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                            .foregroundStyle(.white)
                    }
                }
            }
        }
        .staffToolbarStyle()
        .sheet(isPresented: $showCreateSession) {
            CreateSessionSheet(controller: controller)
        }
        .sheet(isPresented: Binding(
            get: { controller.state.assignDialog != nil },
            set: { if !$0 { controller.dismissAssignDialog() } }
        )) {
            AssignClientSheet(controller: controller)
        }
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
                onTypeFilterSelected: { controller.onScheduleTypeFilterSelected($0) },
                onSessionTap: { controller.openAssignDialog(for: $0) },
                onPrevPeriod: { controller.shiftSchedulePeriod(-7) },
                onNextPeriod: { controller.shiftSchedulePeriod(7) }
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
                    if controller.state.home.needNotificationsPermission {
                        StaffInfoBanner(
                            text: "Push-уведомления выключены — вы не получите оповещения о обращениях и записях."
                        )
                        StaffPrimaryButton(text: "Включить уведомления") {
                            controller.handleAction("enable_notifications")
                        }
                    }
                    if !controller.state.home.metrics.isEmpty {
                        StaffMetricsRow(metrics: controller.state.home.metrics)
                    }
                    if controller.state.home.showAdminButton {
                        StaffPrimaryButton(text: "Открыть админку") {
                            controller.handleAction("open_admin")
                        }
                    }
                    if controller.state.home.showEntryQr {
                        StaffEntryQrCard(
                            staffUserId: controller.state.home.entryQrStaffUserId,
                            rentalActive: controller.state.home.entryQrActive,
                            blockedMessage: controller.state.home.entryQrBlockedMessage,
                            entryQrFormat: controller.state.home.entryQrFormat,
                            compact: true
                        )
                        StaffSecondaryButton(text: "Открыть на весь экран") {
                            controller.handleAction("open_entry_qr")
                        }
                    }
                    if !controller.state.home.sections.isEmpty {
                        ForEach(controller.state.home.sections) { section in
                            StaffSectionTitle(title: section.title)
                            if section.items.isEmpty {
                                StaffEmptyState(message: section.emptyMessage ?? "Нет записей")
                            } else {
                                ForEach(section.items) { item in
                                    StaffListCard(
                                        item: item,
                                        onClick: item.isClickable ? { controller.handleListCardClick(item) } : nil
                                    )
                                }
                            }
                        }
                    } else {
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
                HStack(spacing: 8) {
                    filterChip("Все", selected: !controller.state.clients.onlyActiveBooking) {
                        if controller.state.clients.onlyActiveBooking {
                            controller.toggleClientsActiveFilter()
                        }
                    }
                    filterChip("С активной записью", selected: controller.state.clients.onlyActiveBooking) {
                        if !controller.state.clients.onlyActiveBooking {
                            controller.toggleClientsActiveFilter()
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                ScrollView {
                    LazyVStack(spacing: 10) {
                        if controller.state.clients.loading {
                            StaffLoadingState(message: "Поиск клиентов...")
                        } else if controller.state.clients.items.isEmpty {
                            StaffEmptyState(
                                message: controller.state.clients.query.isEmpty
                                    ? "Клиенты не найдены"
                                    : "По запросу ничего не найдено"
                            )
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

    private func filterChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.caption)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(selected ? StaffColors.primary : StaffColors.primary.opacity(0.12))
                .foregroundStyle(selected ? StaffColors.onPrimary : StaffColors.primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
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
                profileHeaderCard
                StaffInfoBanner(
                    text: "Email и пароль для входа можно изменить только через администратора клуба — обратитесь на ресепшн.",
                    color: StaffColors.onSurfaceVariant
                )
                if let rental = controller.state.profile.rentalPaidUntilLabel {
                    StaffInfoBanner(text: rental, color: StaffColors.primary)
                }
                if controller.state.profile.paidRentalClubs.count > 1 {
                    StaffSectionTitle(title: "Рабочий адрес")
                    ForEach(controller.state.profile.paidRentalClubs) { club in
                        let isActive = club.clubId == controller.state.profile.activeClubId
                        StaffSecondaryButton(
                            text: isActive ? "✓ \(club.title)" : club.title
                        ) {
                            controller.handleAction("set_active_club:\(club.clubId)")
                        }
                    }
                }
                if controller.state.profile.showClubEntryQr {
                    StaffSecondaryButton(text: "QR прохода в зал") {
                        controller.handleAction("open_entry_qr")
                    }
                }
                if controller.state.profile.showRentalManage {
                    StaffSecondaryButton(text: "Аренда и платежи") {
                        controller.handleAction("open_rental")
                    }
                }
                if controller.state.profile.showFeedback {
                    StaffSecondaryButton(text: "Написать в клуб") {
                        controller.handleAction("open_feedback")
                    }
                }
                if controller.state.profile.showTrainerProfileEdit {
                    StaffPrimaryButton(text: "Редактировать профиль специалиста") {
                        controller.handleAction("edit_trainer_profile")
                    }
                }
                StaffSectionTitle(title: "Документы")
                StaffSecondaryButton(text: "Пользовательское соглашение") {
                    controller.handleAction("open_user_agreement")
                }
                StaffSecondaryButton(text: "Политика конфиденциальности") {
                    controller.handleAction("open_privacy")
                }
                StaffSecondaryButton(text: "Договор с Клубом") {
                    controller.handleAction("open_pro_offer")
                }
                StaffSecondaryButton(text: "Иные документы Клуба") {
                    controller.handleAction("open_docs")
                }
                if controller.state.profile.showAdminButton {
                    StaffPrimaryButton(text: "Открыть админку") {
                        controller.handleAction("open_admin")
                    }
                    StaffInfoBanner(text: "Админка CRM доступна")
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

    private var profileHeaderCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Group {
                    if let urlStr = controller.state.profile.photoUrl, let url = URL(string: urlStr) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let img):
                                img.resizable().scaledToFill()
                            default:
                                Circle().fill(StaffColors.primary.opacity(0.15))
                            }
                        }
                    } else {
                        Circle().fill(StaffColors.primary.opacity(0.15))
                            .overlay(Image(systemName: "person.fill").foregroundStyle(StaffColors.primary))
                    }
                }
                .frame(width: 72, height: 72)
                .clipShape(Circle())

                VStack(alignment: .leading, spacing: 4) {
                    Text(controller.state.profile.name.isEmpty ? "Специалист" : controller.state.profile.name)
                        .font(.headline)
                    Text(controller.state.profile.roleTitle)
                        .font(.subheadline)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .background(StaffColors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

            if !controller.state.profile.email.isEmpty {
                profileInfoRow(label: "Email", value: controller.state.profile.email)
            }
            if !controller.state.profile.phone.isEmpty {
                profileInfoRow(label: "Телефон", value: controller.state.profile.phone)
            }
            if !controller.state.profile.specialization.isEmpty {
                profileInfoRow(label: "Специализация", value: controller.state.profile.specialization)
            }
            if !controller.state.profile.description.isEmpty {
                Text(controller.state.profile.description)
                    .font(.caption)
                    .foregroundStyle(StaffColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func profileInfoRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(StaffColors.onSurfaceVariant)
                .frame(width: 130, alignment: .leading)
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(StaffColors.onSurface)
            Spacer(minLength: 0)
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
