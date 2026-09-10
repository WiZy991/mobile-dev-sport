import SwiftUI
import UIKit

/// Добровольно-принудительное обновление: если на сервере `ios_min_version`
/// (например `1.0.9`) выше текущей Version (`CFBundleShortVersionString`) — диалог.
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
        guard needsUpdate(updateInfo) else { return }
        await MainActor.run { update = updateInfo }
    }

    /// Сначала маркетинговая Version (`1.0.8`); иначе fallback на Build (старый `ios_min_version_code`).
    private func needsUpdate(_ info: AppUpdateInfo) -> Bool {
        let minVersion = info.iosMinVersion?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !minVersion.isEmpty {
            return AppVersionCompare.isLessThan(AppConfiguration.appVersion, minVersion)
        }
        let minBuild = info.iosMinVersionCode
        return minBuild > 0 && minBuild > AppConfiguration.appBuildNumber
    }
}

enum AppVersionCompare {
    /// Semver-подобное сравнение: `1.0.8` < `1.0.9`, `1.0` < `1.0.1`.
    static func isLessThan(_ current: String, _ minimum: String) -> Bool {
        let c = parts(current)
        let m = parts(minimum)
        let n = max(c.count, m.count)
        guard n > 0 else { return false }
        for i in 0..<n {
            let a = i < c.count ? c[i] : 0
            let b = i < m.count ? m[i] : 0
            if a != b { return a < b }
        }
        return false
    }

    private static func parts(_ raw: String) -> [Int] {
        raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: ".")
            .map { Int($0.filter(\.isNumber)) ?? 0 }
    }
}

extension View {
    func forceUpdateGate() -> some View {
        modifier(ForceUpdateGate())
    }
}
