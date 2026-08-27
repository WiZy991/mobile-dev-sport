import SwiftUI

struct StaffEntryQrCard: View {
    let staffUserId: Int
    let rentalActive: Bool
    let blockedMessage: String?
    var entryQrFormat: String = "ascii"
    var compact: Bool = false

    @State private var secondsLeft = 15
    @State private var qrImage: UIImage?
    @State private var rotationTask: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 8) {
            Text("Проход в зал")
                .font(.title3.weight(.semibold))
                .foregroundStyle(StaffColors.onSurface)
            if !rentalActive || staffUserId <= 0 {
                StaffInfoBanner(
                    text: blockedMessage ?? "Оплатите аренду клуба, чтобы пройти в зал по QR."
                )
            } else {
                Text("Покажите код на турникете")
                    .font(.subheadline)
                    .foregroundStyle(StaffColors.onSurfaceVariant)
                Group {
                    if let qrImage {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: compact ? 200 : 260, height: compact ? 200 : 260)
                    } else {
                        Color.clear
                            .frame(width: compact ? 200 : 260, height: compact ? 200 : 260)
                    }
                }
                Text("\(secondsLeft) с")
                    .font(.title.weight(.bold))
                    .foregroundStyle(StaffColors.primary)
                Text("Код обновляется каждые 15 секунд")
                    .font(.caption)
                    .foregroundStyle(StaffColors.onSurfaceVariant)
            }
        }
        .padding(compact ? 16 : 20)
        .frame(maxWidth: .infinity)
        .background(StaffColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 4, y: 2)
        .onAppear { restartRotation() }
        .onChange(of: staffUserId) { _, _ in restartRotation() }
        .onChange(of: rentalActive) { _, _ in restartRotation() }
        .onChange(of: entryQrFormat) { _, _ in restartRotation() }
        .onDisappear { rotationTask?.cancel() }
    }

    private func restartRotation() {
        rotationTask?.cancel()
        qrImage = nil
        guard rentalActive, staffUserId > 0 else { return }
        rotationTask = Task { @MainActor in
            while !Task.isCancelled {
                let ms = Int64(Date().timeIntervalSince1970 * 1000)
                let payload = StaffEntryQrCodec.buildPayload(
                    staffUserId: staffUserId,
                    entryQrFormat: entryQrFormat,
                    timestampMillis: ms
                )
                let dim: CGFloat = compact ? 512 : 720
                qrImage = await StaffEntryQrCodec.imageAsync(from: payload, dimension: dim)
                secondsLeft = 15
                for _ in 0..<15 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    if Task.isCancelled { return }
                    secondsLeft = max(0, secondsLeft - 1)
                }
            }
        }
    }
}

struct StaffEntryQrScreen: View {
    let staffUserId: Int
    let rentalActive: Bool
    let blockedMessage: String?
    var entryQrFormat: String = "ascii"
    let onBack: () -> Void

    var body: some View {
        VStack {
            StaffEntryQrCard(
                staffUserId: staffUserId,
                rentalActive: rentalActive,
                blockedMessage: blockedMessage,
                entryQrFormat: entryQrFormat
            )
            .padding(24)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(StaffColors.background)
        .navigationTitle("Проход в зал")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Назад", action: onBack)
            }
        }
        .staffToolbarStyle()
    }
}
