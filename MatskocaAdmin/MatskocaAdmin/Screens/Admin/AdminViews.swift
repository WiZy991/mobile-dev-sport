import SwiftUI

struct AdminHubView: View {
    @State private var controller: AdminHubController
    var onBack: () -> Void
    var onSectionClick: (String) -> Void

    init(env: AppEnvironment, onBack: @escaping () -> Void, onSectionClick: @escaping (String) -> Void) {
        _controller = State(wrappedValue: AdminHubController(env: env))
        self.onBack = onBack
        self.onSectionClick = onSectionClick
    }

    var body: some View {
        Group {
            if controller.state.loading {
                StaffLoadingState(message: "Загрузка админки...")
            } else if let error = controller.state.error {
                StaffErrorState(message: error, onRetry: { controller.loadData() })
                    .padding(16)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        StaffHeroCard(
                            title: "CRM",
                            subtitle: controller.state.canWrite ? "Редактирование разрешено" : "Только просмотр"
                        )
                        if !controller.state.metrics.isEmpty {
                            StaffMetricsRow(metrics: controller.state.metrics)
                        }
                        StaffSectionTitle(title: "Разделы")
                        if controller.state.sections.isEmpty {
                            StaffEmptyState(message: "Нет доступных разделов")
                        } else {
                            StaffMenuCard(
                                title: "Откройте раздел",
                                items: controller.state.sections.map { section in
                                    (SectionIcons.forSection(section.key), section.title, section.hint)
                                },
                                onItemClick: { index in
                                    onSectionClick(controller.state.sections[index].key)
                                }
                            )
                        }
                    }
                    .padding(16)
                }
            }
        }
        .background(StaffColors.background)
        .navigationTitle("Админка")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(.white)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { controller.logout() } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .foregroundStyle(.white)
                }
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.loadData() }
    }
}

struct AdminSectionView: View {
    @State private var controller: AdminSectionController
    var onBack: () -> Void

    init(env: AppEnvironment, section: String, onBack: @escaping () -> Void, onShortcut: @escaping (WorkTab) -> Void, onOpenClient: @escaping (Int) -> Void) {
        let c = AdminSectionController(env: env, section: section)
        c.onShortcut = onShortcut
        c.onOpenClient = onOpenClient
        _controller = State(initialValue: c)
        self.onBack = onBack
    }

    var body: some View {
        Group {
            if controller.state.loading {
                StaffLoadingState()
            } else if let error = controller.state.error {
                StaffErrorState(message: error, onRetry: { controller.loadSection() })
                    .padding(16)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if !controller.state.summary.isEmpty {
                            StaffInfoBanner(text: controller.state.summary)
                        }
                        if !controller.state.metrics.isEmpty {
                            StaffMetricsRow(metrics: controller.state.metrics)
                        }
                        if !controller.state.shortcuts.isEmpty {
                            StaffActionButtons(actions: controller.state.shortcuts) { controller.handleAction($0) }
                        }
                        StaffSectionTitle(title: "Записи")
                        if controller.state.items.isEmpty {
                            StaffEmptyState(message: "В этом разделе пока пусто")
                        } else {
                            ForEach(controller.state.items) { item in
                                StaffListCard(item: item, onClick: item.isClickable ? { controller.handleItemClick(item) } : nil)
                            }
                        }
                        Spacer().frame(height: 8)
                    }
                    .padding(16)
                }
            }
        }
        .background(StaffColors.background)
        .navigationTitle(controller.state.title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(.white)
                }
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.loadSection() }
    }
}

struct ClientDetailView: View {
    @State private var controller: ClientDetailController
    var onBack: () -> Void

    init(env: AppEnvironment, clientId: Int, onBack: @escaping () -> Void) {
        _controller = State(initialValue: ClientDetailController(env: env, clientId: clientId))
        self.onBack = onBack
    }

    var body: some View {
        Group {
            if controller.state.loading {
                StaffLoadingState(message: "Загрузка карточки...")
            } else if let error = controller.state.error {
                StaffErrorState(message: error, onRetry: { controller.loadClient() })
                    .padding(16)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        StaffHeroCard(
                            title: controller.state.name,
                            subtitle: [controller.state.email, controller.state.phone]
                                .filter { !$0.isEmpty }
                                .joined(separator: "\n")
                        )
                        if controller.state.isBlocked {
                            StaffInfoBanner(text: "Клиент заблокирован", color: StaffColors.error)
                        }
                        StaffListCard(item: ListCardUi(
                            title: "Бонусы",
                            subtitle: "\(controller.state.bonusPoints) баллов"
                        ))
                        StaffSectionTitle(title: "Абонемент")
                        StaffListCard(item: ListCardUi(
                            title: controller.state.subscriptionTitle.isEmpty ? "Нет активного абонемента" : controller.state.subscriptionTitle,
                            subtitle: controller.state.subscriptionMeta,
                            badge: "Абонемент",
                            badgeColor: .primary
                        ))
                        if controller.state.showCallButton {
                            StaffPrimaryButton(text: "Позвонить") {
                                if let phone = controller.phone?.trimmingCharacters(in: .whitespacesAndNewlines),
                                   !phone.isEmpty,
                                   let url = URL(string: "tel:\(phone)") {
                                    UIApplication.shared.open(url)
                                }
                            }
                        }
                        StaffSectionTitle(title: "Последние записи")
                        if controller.state.bookings.isEmpty {
                            StaffEmptyState(message: "Нет записей", icon: "calendar")
                        } else {
                            ForEach(controller.state.bookings) { StaffListCard(item: $0) }
                        }
                        StaffSectionTitle(title: "Обращения")
                        if controller.state.tickets.isEmpty {
                            StaffEmptyState(message: "Нет обращений", icon: "headphones")
                        } else {
                            ForEach(controller.state.tickets) { StaffListCard(item: $0) }
                        }
                        Spacer().frame(height: 16)
                    }
                    .padding(16)
                }
            }
        }
        .background(StaffColors.background)
        .navigationTitle(controller.state.title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(.white)
                }
            }
        }
        .staffToolbarStyle()
        .onAppear { controller.loadClient() }
    }
}
