import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var showRegister = false

    var body: some View {
        Group {
            if app.isLoggedIn {
                MainShellView()
            } else {
                LoginView {
                    showRegister = true
                }
                .onChange(of: app.isLoggedIn) { _, loggedIn in
                    if loggedIn { showRegister = false }
                }
                .sheet(isPresented: $showRegister) {
                    RegisterClubPickView()
                        .environmentObject(app)
                }
            }
        }
        .preferredColorScheme(.light)
    }
}

#Preview {
    ContentView()
        .environmentObject(WorldFitnessAppState())
}
