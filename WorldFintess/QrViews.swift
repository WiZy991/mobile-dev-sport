import Combine
import SwiftUI

// MARK: - QR rotation (как `QrCodeViewModel` на Android: 15 сек + ручное обновление)

@MainActor
private final class QRCodeRotationModel: ObservableObject {
    @Published private(set) var payload: String?
    @Published private(set) var qrImage: UIImage?
    @Published private(set) var secondsRemaining: Int = 0

    private var loopTask: Task<Void, Never>?
    private var imageTask: Task<Void, Never>?
    private let qrDimension: CGFloat

    init(qrDimension: CGFloat = 240) {
        self.qrDimension = qrDimension
    }

    func activate(userId: String?, entryQrFormat: String?) {
        loopTask?.cancel()
        imageTask?.cancel()
        guard let userId, !userId.isEmpty else {
            payload = nil
            qrImage = nil
            secondsRemaining = 0
            return
        }
        loopTask = Task { @MainActor in
            while !Task.isCancelled {
                let ts = Int64(Date().timeIntervalSince1970 * 1000)
                let next = QRCodeGenerator.entryPayload(userId: userId, entryQrFormat: entryQrFormat, timestampMillis: ts)
                payload = next
                renderImage(for: next)
                secondsRemaining = 15
                for _ in 0..<15 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    if Task.isCancelled { return }
                    secondsRemaining = max(0, secondsRemaining - 1)
                }
            }
        }
    }

    func refresh(userId: String?, entryQrFormat: String?) {
        activate(userId: userId, entryQrFormat: entryQrFormat)
    }

    func stop() {
        loopTask?.cancel()
        imageTask?.cancel()
        loopTask = nil
        imageTask = nil
    }

    private func renderImage(for payload: String) {
        imageTask?.cancel()
        let dim = qrDimension
        imageTask = Task { @MainActor in
            let img = await QRCodeGenerator.imageAsync(from: payload, dimension: dim)
            guard !Task.isCancelled else { return }
            qrImage = img
        }
    }
}

/// Лист FAB / быстрый вход-выход: компактный QR («ModalBottomSheet» на Android).
struct QrAccessSheetContent: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onClose: (() -> Void)?
    @StateObject private var rotor = QRCodeRotationModel(qrDimension: 280)
    @State private var isInsideGym = false
    @State private var entryBlockedMessage: String?
    @State private var checkingAccess = true

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text(isInsideGym ? "Выход из зала" : "Вход/выход в зал")
                        .font(FCTypography.titleLarge())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)

                    if let u = app.currentUser {
                        Text(u.name)
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }

                    sheetQrSection

                    Text(
                        isInsideGym
                            ? "Поднесите к сканеру на выходе"
                            : (entryBlockedMessage ?? "Поднесите к сканеру")
                    )
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(entryBlockedMessage == nil ? Theme.onSurfaceVariant : Theme.error)
                        .multilineTextAlignment(.center)

                    if let onClose {
                        FCPrimaryButton(title: "Закрыть", isLoading: false) {
                            onClose()
                        }
                        .padding(.top, 8)
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity)
            }
        }
        .task { await refreshGate() }
        .task {
            // Как Android: опрос access status ~каждые 8 с, пока экран открыт.
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard !Task.isCancelled else { return }
                await pollAccessStatus()
            }
        }
        .onDisappear { rotor.stop() }
    }

    @ViewBuilder
    private var sheetQrSection: some View {
        if checkingAccess {
            ProgressView()
                .tint(Theme.primary)
        } else if let msg = entryBlockedMessage, !isInsideGym {
            Text(msg)
                .font(FCTypography.bodyMedium())
                .foregroundStyle(Theme.error)
                .multilineTextAlignment(.center)
                .padding(16)
                .frame(maxWidth: .infinity)
                .background(Theme.error.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
        } else if let img = rotor.qrImage {
            SecureQRImageView(image: img)
                .frame(width: 248, height: 248)
                .padding(16)
                .background(Theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))
                .shadow(color: Color.black.opacity(0.08), radius: 4, x: 0, y: 2)

            if rotor.secondsRemaining > 0 {
                Text("Обновление кода через \(rotor.secondsRemaining) сек")
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.primary)
            }
        } else if app.currentUser == nil {
            Text("Войдите в аккаунт")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.error)
        } else {
            ProgressView()
                .tint(Theme.primary)
        }
    }

    private func pollAccessStatus() async {
        let inside = (try? await app.api.getAccessStatus(forceRefresh: true))?.isInside ?? isInsideGym
        let changed = inside != isInsideGym
        isInsideGym = inside
        if changed {
            await refreshGate()
        }
    }

    private func refreshGate() async {
        checkingAccess = true
        defer { checkingAccess = false }
        isInsideGym = (try? await app.api.getAccessStatus(forceRefresh: true))?.isInside ?? false
        if isInsideGym {
            entryBlockedMessage = nil
            rotor.activate(userId: app.currentUser?.id, entryQrFormat: app.currentUser?.entryQrFormat)
            return
        }
        entryBlockedMessage = await QrEntryGate.blockReason(api: app.api)
        if entryBlockedMessage == nil {
            rotor.activate(userId: app.currentUser?.id, entryQrFormat: app.currentUser?.entryQrFormat)
        } else {
            rotor.stop()
        }
    }
}

/// Полноэкранный экран: `QrCodeScreen.kt` — «Электронная карта», инструкции, обновление, таймер 15 сек.
struct QrFullScreenView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @StateObject private var rotor = QRCodeRotationModel(qrDimension: 560)
    @State private var isInsideGym = false
    @State private var entryBlockedMessage: String?
    @State private var checkingAccess = true

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {
                    Spacer().frame(height: 8)

                    if let u = app.currentUser {
                        Text(u.name)
                            .font(FCTypography.headlineMedium())
                            .fontWeight(.bold)
                            .foregroundStyle(Theme.onBackground)
                            .multilineTextAlignment(.center)

                        Spacer().frame(height: 8)

                        Text("ID: \(u.id)")
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }

                    Spacer().frame(height: 16)

                    Text(isInsideGym ? "Выход из зала" : "Вход в зал")
                        .font(FCTypography.titleMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Theme.onBackground)

                    Spacer().frame(height: 16)

                    fullScreenQrCard

                    Spacer().frame(height: 24)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Как использовать")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onBackground)
                        Text(
                            isInsideGym
                                ? "Поднесите QR-код к сканеру при выходе. Турникет откроется, посещение не спишется повторно."
                                : (entryBlockedMessage
                                    ?? "Поднесите QR-код к сканеру на входе или выходе. Турникет откроется автоматически.")
                        )
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(entryBlockedMessage == nil || isInsideGym ? Theme.onSurfaceVariant : Theme.error)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.primary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))

                    Spacer().frame(height: 24)

                    if entryBlockedMessage == nil || isInsideGym {
                        qrRefreshOutlinedButton(userId: app.currentUser?.id)
                        Spacer().frame(height: 8)
                        Text(qrExpiryFooter)
                            .font(FCTypography.bodySmall())
                            .foregroundStyle(Theme.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }

                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 24)
                .frame(maxWidth: .infinity)
            }
        }
        .fcPrimaryNavigation(title: "Электронная карта")
        .background(Theme.background)
        .task { await refreshGate() }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                guard !Task.isCancelled else { return }
                await pollAccessStatus()
            }
        }
        .onDisappear { rotor.stop() }
    }

    private func pollAccessStatus() async {
        let inside = (try? await app.api.getAccessStatus(forceRefresh: true))?.isInside ?? isInsideGym
        let changed = inside != isInsideGym
        isInsideGym = inside
        if changed {
            await refreshGate()
        }
    }

    private var qrExpiryFooter: String {
        if rotor.secondsRemaining > 0 {
            return "Код обновится через \(rotor.secondsRemaining) сек (действует 15 сек)"
        }
        return "Обновление…"
    }

    @ViewBuilder
    private var fullScreenQrCard: some View {
        if app.currentUser == nil {
            Text("Войдите в аккаунт")
                .font(FCTypography.bodyLarge())
                .foregroundStyle(Theme.error)
        } else if checkingAccess {
            ZStack {
                Theme.surface
                ProgressView().tint(Theme.primary)
            }
            .frame(width: 320, height: 320)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        } else if let msg = entryBlockedMessage, !isInsideGym {
            ZStack {
                Theme.surface
                Text(msg)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(Theme.error)
                    .multilineTextAlignment(.center)
                    .padding(24)
            }
            .frame(width: 320, height: 320)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        } else if let img = rotor.qrImage {
            ZStack {
                Theme.surface
                SecureQRImageView(image: img)
                    .padding(24)
            }
            .frame(width: 320, height: 320)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        } else {
            ZStack {
                Theme.surface
                ProgressView()
                    .tint(Theme.primary)
            }
            .frame(width: 320, height: 320)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        }
    }

    private func qrRefreshOutlinedButton(userId: String?) -> some View {
        Button {
            Task { await refreshGate() }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.clockwise")
                    .font(FCTypography.titleMedium())
                Text("Обновить QR-код")
                    .font(FCTypography.titleMedium())
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .foregroundStyle(Theme.primary)
        }
        .buttonStyle(.plain)
        .background(
            RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous)
                .stroke(Theme.onSurface.opacity(0.38), lineWidth: 1)
        )
        .opacity(userId == nil ? 0.5 : 1)
        .disabled(userId == nil)
    }

    private func refreshGate() async {
        checkingAccess = true
        defer { checkingAccess = false }
        isInsideGym = (try? await app.api.getAccessStatus(forceRefresh: true))?.isInside ?? false
        if isInsideGym {
            entryBlockedMessage = nil
            rotor.activate(userId: app.currentUser?.id, entryQrFormat: app.currentUser?.entryQrFormat)
            return
        }
        entryBlockedMessage = await QrEntryGate.blockReason(api: app.api)
        if entryBlockedMessage == nil {
            rotor.activate(userId: app.currentUser?.id, entryQrFormat: app.currentUser?.entryQrFormat)
        } else {
            rotor.stop()
        }
    }
}

enum QrEntryGate {
    static func blockReason(api: FitnessAPI) async -> String? {
        guard let subs = try? await api.getMySubscriptions(forceRefresh: true) else { return nil }
        let active = subs.filter { $0.status == .active && !$0.isFrozen }
        if active.isEmpty {
            return "Нет активного абонемента. Оформите абонемент, чтобы войти в зал."
        }
        let hasVisits = active.contains { sub in
            guard let left = sub.visitsLeft else { return true }
            return left > 0
        }
        if !hasVisits {
            return "Посещения по абонементу закончились. Купите новый или продлите текущий."
        }
        return nil
    }
}
