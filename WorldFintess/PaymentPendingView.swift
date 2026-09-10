import Combine
import SwiftUI

/// Ожидание подтверждения оплаты через Альфа-Банк (polling `GET /payments/{id}/status`).
struct PaymentPendingView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @Environment(\.scenePhase) private var scenePhase

    let paymentId: Int
    var onSuccess: () -> Void
    var onFailed: (String) -> Void

    @State private var statusMessage = "Ожидаем подтверждение оплаты…"
    @State private var isFailed = false
    @State private var pollGeneration = 0

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            VStack(spacing: 24) {
                if isFailed {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(Theme.error)
                } else {
                    ProgressView()
                        .controlSize(.large)
                }
                Text(statusMessage)
                    .font(FCTypography.titleMedium())
                    .fontWeight(.semibold)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.onBackground)
                    .padding(.horizontal, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .fcPrimaryNavigation(title: "Оплата")
        .task(id: pollGeneration) {
            await pollPaymentStatus()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                pollGeneration += 1
            }
        }
        .onReceive(PaymentDeepLinkBus.publisher) { note in
            guard let returnedId = note.object as? Int, returnedId == paymentId else { return }
            pollGeneration += 1
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Отмена") {
                    isFailed = true
                    statusMessage = "Ожидание оплаты остановлено. Если деньги списались — проверьте абонемент в профиле."
                    onFailed(statusMessage)
                }
            }
        }
    }

    private func pollPaymentStatus() async {
        let maxAttempts = 120
        for attempt in 0..<maxAttempts {
            if Task.isCancelled { return }
            if await checkOnce() { return }
            let delayNs: UInt64 = attempt < 10 ? 1_000_000_000 : 2_000_000_000
            try? await Task.sleep(nanoseconds: delayNs)
        }

        await MainActor.run {
            isFailed = true
            statusMessage = "Не удалось подтвердить оплату. Если деньги списались — откройте профиль и проверьте абонемент."
            onFailed(statusMessage)
        }
    }

    private func checkOnce() async -> Bool {
        do {
            let data = try await app.api.getPaymentStatus(paymentId: paymentId)
            return await handleStatus(data)
        } catch let e as FitnessAPIError {
            if case .http(let code, _) = e, code == 401 || code == 403 {
                await MainActor.run {
                    isFailed = true
                    statusMessage = "Сессия истекла. Войдите снова и проверьте абонемент в профиле — оплата могла уже пройти."
                    onFailed(statusMessage)
                }
                return true
            }
            return false
        } catch {
            return false
        }
    }

    @MainActor
    private func handleStatus(_ data: SubscriptionPaymentInitResponse) -> Bool {
        switch data.status {
        case "paid":
            statusMessage = "Оплата прошла успешно"
            onSuccess()
            return true
        case "failed", "expired", "cancelled":
            isFailed = true
            statusMessage = data.failureReason ?? {
                switch data.status {
                case "expired": return "Время оплаты истекло"
                case "cancelled": return "Оплата отменена"
                default: return "Оплата не прошла"
                }
            }()
            onFailed(statusMessage)
            return true
        default:
            return false
        }
    }
}
