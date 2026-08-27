import SwiftUI

/// `MainScreen.kt`: NavigationBar (4 tabs) ✅ FAB только на главной ✅ sheet QR.
struct MainShellView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    /// Лениво монтируем вкладки и не уничтожаем — иначе каждый switch заново бьёт API.
    @State private var mountedTabs: Set<Int> = [0]
    @State private var path = NavigationPath()
    @State private var showQrSheet = false
    @State private var needsResumeUnlock = false
    @State private var showSecuritySetup = false

    var body: some View {
        ZStack {
        NavigationStack(path: $path) {
            ZStack(alignment: .bottomTrailing) {
                ZStack {
                    if mountedTabs.contains(0) {
                        HomeTabView(
                            go: { path.append($0) },
                            openQrSheet: { showQrSheet = true },
                            switchToProfileTab: { selectTab(2) },
                            isActive: tab == 0
                        )
                        .opacity(tab == 0 ? 1 : 0)
                        .allowsHitTesting(tab == 0)
                        .accessibilityHidden(tab != 0)
                    }
                    if mountedTabs.contains(1) {
                        MyBookingsTabView { path.append($0) }
                            .opacity(tab == 1 ? 1 : 0)
                            .allowsHitTesting(tab == 1)
                            .accessibilityHidden(tab != 1)
                    }
                    if mountedTabs.contains(2) {
                        ProfileTabView { path.append($0) }
                            .opacity(tab == 2 ? 1 : 0)
                            .allowsHitTesting(tab == 2)
                            .accessibilityHidden(tab != 2)
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
                Group {
                    if AppPinStore.isPinEnabled {
                        AppSecurityLockOverlay {
                            needsResumeUnlock = false
                        }
                    } else {
                        AppResumeLockOverlay {
                            needsResumeUnlock = false
                        }
                    }
                }
                .zIndex(1000)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            guard app.isLoggedIn else { return }
            switch phase {
            case .background:
                AppResumeLock.markEnteredBackground()
            case .active:
                if AppResumeLock.shouldRequireUnlockOnResume() {
                    needsResumeUnlock = true
                }
                AppResumeLock.clearInactivityMarker()
            default:
                break
            }
        }
        .onAppear {
            if app.isLoggedIn, AppResumeLock.shouldRequireUnlockOnResume() {
                needsResumeUnlock = true
            }
            AppResumeLock.clearInactivityMarker()
            if app.pendingSecuritySetup { showSecuritySetup = true }
        }
        .onChange(of: app.paymentNavigationRequest) { _, paymentId in
            guard let paymentId, paymentId > 0 else { return }
            app.paymentNavigationRequest = nil
            path.append(AppRoute.paymentPending(paymentId))
        }
        .onReceive(PaymentDeepLinkBus.publisher) { note in
            guard app.isLoggedIn, let paymentId = note.object as? Int, paymentId > 0 else { return }
            if app.currentPaymentId == paymentId { return }
            path.append(AppRoute.paymentPending(paymentId))
        }
        .onChange(of: app.pendingSecuritySetup) { _, pending in
            if pending { showSecuritySetup = true }
        }
        .sheet(isPresented: $showSecuritySetup, onDismiss: {
            app.pendingSecuritySetup = false
        }) {
            PostRegistrationSecuritySetupView()
                .environmentObject(app)
        }
    }

    private func selectTab(_ index: Int) {
        mountedTabs.insert(index)
        tab = index
    }

    private var fcBottomBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 0) {
                fcTabItem(0, "Главная", "house.fill", "house")
                // TODO(restore): вкладка «Расписание» временно скрыта (как на Android).
                fcTabItem(1, "Мои записи", "figure.strengthtraining.traditional", "figure.strengthtraining.traditional")
                fcTabItem(2, "Профиль", "person.fill", "person")
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
            selectTab(index)
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
            SubscriptionPlansView(onNavigate: { path.append($0) })
        case .paymentPending(let paymentId):
            PaymentPendingView(
                paymentId: paymentId,
                onSuccess: {
                    app.currentPaymentId = nil
                    app.subscriptionsRevision = UUID()
                    selectTab(2)
                    path = NavigationPath()
                },
                onFailed: { _ in
                    app.currentPaymentId = nil
                    if !path.isEmpty {
                        path.removeLast()
                    }
                }
            )
            .onAppear { app.currentPaymentId = paymentId }
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
        case .changePassword:
            ChangePasswordView()
        case .networkInfo:
            NetworkInfoView()
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
        case .selectPreferredClub:
            SelectPreferredClubView()
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
        case .trainingDiary:
            TrainingDiaryView()
        case .legalDocument(let kind):
            LegalDocumentView(document: kind)
        case .legalPdf(let asset):
            LegalPdfView(asset: asset)
        }
    }
}
