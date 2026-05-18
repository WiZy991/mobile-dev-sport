import LocalAuthentication
import SwiftUI

/// Блокировка при уходе в фон: разблокировка через Face ID / Touch ID или код-пароль устройства (`deviceOwnerAuthentication`).
enum AppResumeLock {
    @MainActor
    static func authenticateUserPresence() async -> Bool {
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            return true
        }
        return await withCheckedContinuation { cont in
            ctx.evaluatePolicy(
                .deviceOwnerAuthentication,
                localizedReason: "Продолжить работу в приложении"
            ) { success, _ in
                cont.resume(returning: success)
            }
        }
    }
}

struct AppResumeLockOverlay: View {
    let onUnlocked: () -> Void

    @State private var didFail = false

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 24) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Theme.primary)
                Text("Приложение заблокировано")
                    .font(FCTypography.titleLarge())
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.onBackground)
                    .multilineTextAlignment(.center)
                Text("Подтвердите личность Face ID, Touch ID или код-паролем устройства.")
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                Button {
                    Task { await tryUnlock() }
                } label: {
                    Text(didFail ? "Повторить" : "Разблокировать")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Theme.primary)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                }
                .padding(.horizontal, 32)
                .padding(.top, 8)
            }
        }
        .onAppear {
            Task { await tryUnlock() }
        }
    }

    private func tryUnlock() async {
        let ok = await AppResumeLock.authenticateUserPresence()
        await MainActor.run {
            if ok {
                onUnlocked()
            } else {
                didFail = true
            }
        }
    }
}
