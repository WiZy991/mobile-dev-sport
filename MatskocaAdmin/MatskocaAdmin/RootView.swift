import SwiftUI

struct RootView: View {
    @State private var env = AppEnvironment()
    @State private var loginController: LoginController?
    @State private var workController: WorkController?
    @State private var isShowingWork = false

    var body: some View {
        Group {
            if isShowingWork || env.isAuthenticated {
                authenticatedStack
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
        }
        .onChange(of: env.isAuthenticated) { _, authenticated in
            isShowingWork = authenticated
            if authenticated {
                if workController == nil {
                    let initialTab = env.pendingWorkTab ?? .home
                    env.pendingWorkTab = nil
                    workController = makeWorkController(initialTab: initialTab)
                }
            } else {
                workController = nil
                loginController = makeLoginController()
            }
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
            if workController == nil {
                workController = makeWorkController()
            }
        }
        loginController = controller
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
        workController = controller
        return controller
    }
}

#Preview {
    RootView()
}
