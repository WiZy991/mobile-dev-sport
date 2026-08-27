import SwiftUI

struct RootView: View {
    @State private var env = AppEnvironment()
    @State private var loginController: LoginController?
    @State private var workController: WorkController?
    @State private var onboardingController: OnboardingController?
    @State private var requiredTrainerProfileController: TrainerProfileController?
    @State private var rentalController: RentalController?
    @State private var isShowingWork = false

    var body: some View {
        Group {
            if isShowingWork || env.isAuthenticated {
                authenticatedRoot
            } else if let loginController {
                LoginView(controller: loginController)
            } else {
                ProgressView()
                    .tint(StaffColors.primary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(StaffColors.primary)
            }
        }
        .onAppear {
            StaffNotificationService.shared.requestPermission()
            StaffNotificationService.shared.onOpenSupport = {
                env.navigateToWorkTab(.support)
            }
            isShowingWork = env.isAuthenticated
            if loginController == nil, !isShowingWork {
                loginController = makeLoginController()
            }
            if env.isAuthenticated {
                Task { await env.resolveAuthGate() }
            }
        }
        .onChange(of: env.isAuthenticated) { _, authenticated in
            isShowingWork = authenticated
            if authenticated {
                Task { await env.resolveAuthGate() }
            } else {
                workController = nil
                onboardingController = nil
                requiredTrainerProfileController = nil
                rentalController = nil
                loginController = makeLoginController()
            }
        }
    }

    @ViewBuilder
    private var authenticatedRoot: some View {
        switch env.authGate {
        case .checking:
            VStack(spacing: 16) {
                if let err = env.authGateError {
                    Text(err)
                        .font(.subheadline)
                        .foregroundStyle(StaffColors.error)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    Button("Повторить") {
                        Task { await env.resolveAuthGate() }
                    }
                    .buttonStyle(.borderedProminent)
                    Button("Выйти") {
                        env.logout()
                        isShowingWork = false
                    }
                } else {
                    ProgressView("Проверяем доступ…")
                        .tint(StaffColors.primary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(StaffColors.background)
            .task {
                if env.authGateError == nil {
                    await env.resolveAuthGate()
                }
            }
        case .onboarding:
            OnboardingView(controller: onboardingController ?? makeOnboardingController())
        case .trainerProfile:
            NavigationStack {
                TrainerProfileView(
                    controller: requiredTrainerProfileController ?? makeRequiredTrainerProfileController(),
                    onBack: nil
                )
            }
        case .work:
            authenticatedStack
        }
    }

    private var authenticatedStack: some View {
        NavigationStack(path: $env.navigationPath) {
            WorkView(controller: workController ?? makeWorkController())
                .navigationDestination(for: AppRoute.self) { route in
                    switch route {
                    case .adminHub:
                        AdminHubView(
                            env: env,
                            onBack: { env.navigationPath.removeLast() },
                            onSectionClick: { section in
                                env.navigationPath.append(.adminSection(section))
                            }
                        )
                    case .adminSection(let section):
                        AdminSectionView(
                            env: env,
                            section: section,
                            onBack: { env.navigationPath.removeLast() },
                            onShortcut: { tab in env.navigateToWorkTab(tab) },
                            onOpenClient: { clientId in env.navigationPath.append(.clientDetail(clientId)) }
                        )
                    case .clientDetail(let clientId):
                        ClientDetailView(
                            env: env,
                            clientId: clientId,
                            onBack: { env.navigationPath.removeLast() }
                        )
                    case .entryQr:
                        StaffEntryQrScreen(
                            staffUserId: workController?.state.home.entryQrStaffUserId ?? 0,
                            rentalActive: workController?.state.home.entryQrActive ?? false,
                            blockedMessage: workController?.state.home.entryQrBlockedMessage,
                            entryQrFormat: workController?.state.home.entryQrFormat ?? "ascii",
                            onBack: { env.navigationPath.removeLast() }
                        )
                    case .rental:
                        RentalView(
                            controller: rentalController ?? makeRentalController(),
                            onBack: { env.navigationPath.removeLast() }
                        )
                    case .feedback:
                        StaffFeedbackView(
                            controller: StaffFeedbackController(env: env),
                            onBack: { env.navigationPath.removeLast() }
                        )
                    case .trainerProfile:
                        TrainerProfileView(
                            controller: TrainerProfileController(env: env, requiredMode: false),
                            onBack: { env.navigationPath.removeLast() }
                        )
                    case .legalPdf(let raw):
                        if let doc = StaffLegalPdf(rawValue: raw) {
                            LegalPdfView(doc: doc) {
                                env.navigationPath.removeLast()
                            }
                        } else {
                            StaffEmptyState(message: "Документ не найден")
                        }
                    }
                }
        }
        .onAppear {
            if workController == nil {
                let initialTab = env.pendingWorkTab ?? .home
                env.pendingWorkTab = nil
                workController = makeWorkController(initialTab: initialTab)
            }
        }
        .onChange(of: env.pendingWorkTab) { _, tab in
            if let tab {
                workController?.selectTab(tab)
                env.pendingWorkTab = nil
            }
        }
    }

    private func makeLoginController() -> LoginController {
        let controller = LoginController(env: env)
        controller.onAuthenticated = {
            env.isAuthenticated = true
            isShowingWork = true
            Task { await env.resolveAuthGate() }
        }
        loginController = controller
        return controller
    }

    private func makeOnboardingController() -> OnboardingController {
        let controller = OnboardingController(env: env)
        controller.onFinished = {
            Task { await env.resolveAuthGate() }
        }
        controller.onLogout = {
            env.logout()
        }
        onboardingController = controller
        return controller
    }

    private func makeRequiredTrainerProfileController() -> TrainerProfileController {
        let controller = TrainerProfileController(env: env, requiredMode: true)
        controller.onFinished = {
            Task { await env.resolveAuthGate() }
        }
        requiredTrainerProfileController = controller
        return controller
    }

    private func makeRentalController() -> RentalController {
        let controller = RentalController(env: env)
        rentalController = controller
        return controller
    }

    private func makeWorkController(initialTab: WorkTab = .home) -> WorkController {
        let controller = WorkController(env: env, initialTab: initialTab)
        controller.onOpenAdmin = {
            env.navigationPath.append(.adminHub)
        }
        controller.onOpenClient = { clientId in
            env.navigationPath.append(.clientDetail(clientId))
        }
        controller.onOpenAdminSection = { section in
            env.navigationPath.append(.adminSection(section))
        }
        controller.onOpenEntryQr = {
            env.navigationPath.append(.entryQr)
        }
        controller.onOpenRental = {
            _ = self.makeRentalController()
            env.navigationPath.append(.rental)
        }
        controller.onOpenFeedback = {
            env.navigationPath.append(.feedback)
        }
        controller.onOpenTrainerProfile = {
            env.navigationPath.append(.trainerProfile)
        }
        controller.onOpenLegalPdf = { doc in
            env.navigationPath.append(.legalPdf(doc.rawValue))
        }
        workController = controller
        return controller
    }
}

#Preview {
    RootView()
}
