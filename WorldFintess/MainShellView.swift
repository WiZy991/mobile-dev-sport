import SwiftUI

/// `MainScreen.kt`: NavigationBar (4 tabs) ✅ FAB только на главной ✅ sheet QR.
struct MainShellView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    @State private var path = NavigationPath()
    @State private var showQrSheet = false
    @State private var needsResumeUnlock = false

    var body: some View {
        ZStack {
        NavigationStack(path: $path) {
            ZStack(alignment: .bottomTrailing) {
                Group {
                    switch tab {
                    case 0:
                        HomeTabView(
                            go: { path.append($0) },
                            openQrSheet: { showQrSheet = true },
                            switchToProfileTab: { tab = 3 },
                            switchToScheduleTab: { tab = 1 }
                        )
                    case 1:
                        ScheduleTabView { path.append($0) }
                    case 2:
                        MyBookingsTabView { path.append($0) }
                    case 3:
                        ProfileTabView { path.append($0) }
                    default:
                        EmptyView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background)

                if tab == 0 {
                    Button {
                        showQrSheet = true
                    } label: {
                        Image(systemName: "qrcode")
                            .font(.system(size: 24, weight: .medium))
                            .foregroundStyle(Theme.onPrimary)
                            .frame(width: 56, height: 56)
                            .background(Theme.primary)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
                            .shadow(color: Color.black.opacity(0.22), radius: 6, x: 0, y: 3)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 16)
                    .padding(.bottom, 8)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                fcBottomBar
            }
            .navigationDestination(for: AppRoute.self) { route in
                routeDestination(route)
            }
        }
        .sheet(isPresented: $showQrSheet) {
            QrAccessSheetContent(onClose: { showQrSheet = false })
                .environmentObject(app)
                .presentationDragIndicator(.visible)
        }

            if needsResumeUnlock && app.isLoggedIn {
                AppResumeLockOverlay {
                    needsResumeUnlock = false
                }
                .zIndex(1000)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background, app.isLoggedIn {
                needsResumeUnlock = true
            }
        }
    }

    private var fcBottomBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 0) {
                fcTabItem(0, "Главная", "house.fill", "house")
                fcTabItem(1, "Расписание", "calendar", "calendar")
                fcTabItem(2, "Мои записи", "figure.strengthtraining.traditional", "figure.strengthtraining.traditional")
                fcTabItem(3, "Профиль", "person.fill", "person")
            }
            .padding(.top, 8)
            .padding(.bottom, 6)
            .background(Theme.surface)
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: -2)
        }
    }

    private func fcTabItem(_ index: Int, _ title: String, _ iconSelected: String, _ iconOutline: String) -> some View {
        let selected = tab == index
        return Button {
            tab = index
        } label: {
            VStack(spacing: 4) {
                Image(systemName: selected ? iconSelected : iconOutline)
                    .font(.system(size: 22))
                Text(title)
                    .font(FCTypography.labelMedium())
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity)
            .foregroundStyle(selected ? Theme.primary : Theme.onSurfaceVariant)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func routeDestination(_ route: AppRoute) -> some View {
        switch route {
        case .trainingDetail(let id):
            TrainingDetailView(trainingId: id)
        case .subscriptionPlans:
            SubscriptionPlansView()
        case .qrCode:
            QrFullScreenView()
        case .editProfile:
            EditProfileView()
        case .referral:
            ReferralView()
        case .notifications:
            NotificationsView()
        case .settings:
            SettingsView()
        case .help:
            HelpView()
        case .about:
            AboutView()
        case .shop:
            ShopView()
        case .clubs:
            ClubsListView()
        case .clubDetail(let id):
            ClubInfoView(clubId: id)
        case .clubInfo:
            ClubInfoView(clubId: nil)
        case .lockers:
            LockersView()
        case .guestPass:
            GuestPassView()
        case .documents:
            DocumentsView()
        case .purchaseHistory:
            PurchaseHistoryView()
        case .trainers:
            TrainersListView()
        case .trainerDetail(let id):
            TrainerDetailView(trainerId: id)
        case .personalTraining:
            PersonalTrainingView()
        }
    }
}
