import SwiftUI

@main
struct MatskocaAdminApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.light)
                .onOpenURL { url in
                    guard url.scheme == "staffapp" || url.scheme == "matskocaadmin" else { return }
                    NotificationCenter.default.post(name: .staffPaymentDeepLink, object: url)
                }
        }
    }
}
