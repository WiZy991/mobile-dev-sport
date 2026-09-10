import SwiftUI

private enum AuthStackRoute: Hashable {
    case login
}

struct ContentView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @AppStorage("appThemeMode") private var themeModeRaw = AppThemeMode.system.rawValue
    @State private var authPath: [AuthStackRoute] = []
    @State private var showRegister = false
    @State private var phoneOtpRegister = false

    var body: some View {
        Group {
            if !app.sessionReady {
                LaunchSplashView()
            } else if app.isLoggedIn {
                MainShellView()
            } else {
                // Как Android: каждый раз при logout — Welcome, затем Login в стеке.
                NavigationStack(path: $authPath) {
                    WelcomeView {
                        authPath.append(.login)
                    }
                    .navigationDestination(for: AuthStackRoute.self) { route in
                        switch route {
                        case .login:
                            LoginView(
                                onRegister: {
                                    phoneOtpRegister = false
                                    showRegister = true
                                },
                                onPhoneRegister: {
                                    phoneOtpRegister = true
                                    showRegister = true
                                }
                            )
                        }
                    }
                }
                .onChange(of: app.isLoggedIn) { _, loggedIn in
                    if loggedIn {
                        showRegister = false
                        phoneOtpRegister = false
                        authPath = []
                    }
                }
                .fullScreenCover(isPresented: $showRegister) {
                    RegisterFlowView(phoneOtpMode: phoneOtpRegister)
                        .environmentObject(app)
                }
            }
        }
        .forceUpdateGate()
        .preferredColorScheme((AppThemeMode(rawValue: themeModeRaw) ?? .system).colorScheme)
    }
}

#Preview {
    ContentView()
        .environmentObject(WorldFitnessAppState())
}
