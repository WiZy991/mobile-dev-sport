import SwiftUI
import UIKit

/// Добровольно-принудительное обновление: если на сервере `ios_min_version_code`
/// выше текущего `CFBundleVersion` — показываем диалог.
/// При force нельзя закрыть (как Android `ForceUpdateGate`).
struct ForceUpdateGate: ViewModifier {
    @EnvironmentObject private var app: WorldFitnessAppState
    @State private var update: AppUpdateInfo?
    @State private var dismissedSoft = false

    func body(content: Content) -> some View {
        content
            .task { await check() }
            .alert(alertTitle, isPresented: Binding(
                get: { shouldShow },
                set: { newValue in
                    if !newValue, !(update?.resolvedForceForIOS ?? false) {
                        dismissedSoft = true
                    }
                }
            )) {
                Button("Обновить") {
                    UIApplication.shared.open(AppConfiguration.appStoreURL)
                }
                if !(update?.resolvedForceForIOS ?? false) {
                    Button("Позже", role: .cancel) {
                        dismissedSoft = true
                    }
                }
            } message: {
                Text(alertMessage)
            }
            .interactiveDismissDisabled(update?.resolvedForceForIOS == true)
    }

    private var shouldShow: Bool {
        guard let update else { return false }
        if !update.resolvedForceForIOS && dismissedSoft { return false }
        return true
    }

    private var alertTitle: String {
        (update?.resolvedForceForIOS == true) ? "Требуется обновление" : "Доступно обновление"
    }

    private var alertMessage: String {
        let msg = update?.resolvedMessageForIOS?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !msg.isEmpty { return msg }
        return "Доступна новая версия приложения. Обновите её, чтобы продолжить пользоваться сервисом."
    }

    private func check() async {
        guard let info = try? await app.api.getClubInfo(),
              let updateInfo = info.appUpdate
        else { return }
        let minBuild = updateInfo.iosMinVersionCode
        guard minBuild > 0, minBuild > AppConfiguration.appBuildNumber else { return }
        await MainActor.run { update = updateInfo }
    }
}

extension View {
    func forceUpdateGate() -> some View {
        modifier(ForceUpdateGate())
    }
}
