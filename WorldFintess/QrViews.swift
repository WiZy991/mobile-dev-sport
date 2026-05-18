import Combine
import SwiftUI

// MARK: - QR rotation (как `QrCodeViewModel` на Android: 15 сек + ручное обновление)

@MainActor
private final class QRCodeRotationModel: ObservableObject {
    @Published private(set) var payload: String?
    @Published private(set) var secondsRemaining: Int = 0

    private var loopTask: Task<Void, Never>?

    func activate(userId: String?) {
        loopTask?.cancel()
        guard let userId, !userId.isEmpty else {
            payload = nil
            secondsRemaining = 0
            return
        }
        loopTask = Task { @MainActor in
            while !Task.isCancelled {
                let ts = Int64(Date().timeIntervalSince1970 * 1000)
                payload = QRCodeGenerator.entryPayload(userId: userId, timestampMillis: ts)
                secondsRemaining = 15
                for _ in 0..<15 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    if Task.isCancelled { return }
                    secondsRemaining = max(0, secondsRemaining - 1)
                }
            }
        }
    }

    func refresh(userId: String?) {
        activate(userId: userId)
    }

    func stop() {
        loopTask?.cancel()
        loopTask = nil
    }
}

/// Лист FAB / быстрый вход: заголовок «Вход в зал», компактный QR («ModalBottomSheet» на Android).
struct QrAccessSheetContent: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    var onClose: (() -> Void)?
    @StateObject private var rotor = QRCodeRotationModel()

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("Вход в зал")
                        .font(FCTypography.titleLarge())
                        .fontWeight(.bold)
                        .foregroundStyle(Theme.onBackground)

                    if let u = app.currentUser {
                        Text(u.name)
                            .font(FCTypography.bodyLarge())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }

                    sheetQrSection

                    Text("Поднесите к сканеру")
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)

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
        .onAppear {
            rotor.activate(userId: app.currentUser?.id)
        }
        .onChange(of: app.currentUser?.id) { _, newId in
            rotor.activate(userId: newId)
        }
        .onDisappear { rotor.stop() }
    }

    @ViewBuilder
    private var sheetQrSection: some View {
        if let payload = rotor.payload, let img = QRCodeGenerator.image(from: payload, dimension: 240) {
            Image(uiImage: img)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .frame(width: 208, height: 208)
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
}

/// Полноэкранный экран: `QrCodeScreen.kt` — «Электронная карта», инструкции, обновление, таймер 15 сек.
struct QrFullScreenView: View {
    @EnvironmentObject private var app: WorldFitnessAppState
    @StateObject private var rotor = QRCodeRotationModel()

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

                    Spacer().frame(height: 24)

                    fullScreenQrCard

                    Spacer().frame(height: 24)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Как использовать")
                            .font(FCTypography.titleMedium())
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.onBackground)
                        Text("Поднесите QR-код к сканеру на входе в клуб. Турникет откроется автоматически.")
                            .font(FCTypography.bodyMedium())
                            .foregroundStyle(Theme.onSurfaceVariant)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.primary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius16, style: .continuous))

                    Spacer().frame(height: 24)

                    qrRefreshOutlinedButton(userId: app.currentUser?.id)

                    Spacer().frame(height: 8)

                    Text(qrExpiryFooter)
                        .font(FCTypography.bodySmall())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)

                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 24)
                .frame(maxWidth: .infinity)
            }
        }
        .fcPrimaryNavigation(title: "Электронная карта")
        .background(Theme.background)
        .onAppear {
            rotor.activate(userId: app.currentUser?.id)
        }
        .onChange(of: app.currentUser?.id) { _, newId in
            rotor.activate(userId: newId)
        }
        .onDisappear { rotor.stop() }
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
        } else if let payload = rotor.payload, let img = QRCodeGenerator.image(from: payload, dimension: 500) {
            ZStack {
                Theme.surface
                Image(uiImage: img)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .padding(24)
            }
            .frame(width: 280, height: 280)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        } else {
            ZStack {
                Theme.surface
                VStack(spacing: 16) {
                    Image(systemName: "qrcode")
                        .font(.system(size: 56))
                        .foregroundStyle(Theme.onSurfaceVariant)
                    Text("Не удалось загрузить QR-код")
                        .font(FCTypography.bodyMedium())
                        .foregroundStyle(Theme.onSurfaceVariant)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
            }
            .frame(width: 280, height: 280)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
        }
    }

    private func qrRefreshOutlinedButton(userId: String?) -> some View {
        Button {
            rotor.refresh(userId: userId)
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
}
