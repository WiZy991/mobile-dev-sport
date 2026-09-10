import SwiftUI

struct StaffEntryQrCard: View {
    let staffUserId: Int
    let rentalActive: Bool
    let blockedMessage: String?
    var entryQrFormat: String = "ascii"
    var hallLabel: String? = nil
    var paidRentalClubs: [RentalClubOption] = []
    var activeClubId: Int? = nil
    var onSelectClub: ((Int) -> Void)? = nil
    var compact: Bool = false

    @State private var secondsLeft = 15
    @State private var qrImage: UIImage?
    @State private var rotationTask: Task<Void, Never>?

    var body: some View {
        VStack(spacing: 8) {
            Text("Проход в зал")
                .font(.title3.weight(.semibold))
                .foregroundStyle(StaffColors.onSurface)
            if let hallLabel, !hallLabel.isEmpty {
                Text("Зал: \(hallLabel)")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(StaffColors.onSurfaceVariant)
            }
            if paidRentalClubs.count > 1, onSelectClub != nil {
                Text("На какой зал QR")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(StaffColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(paidRentalClubs) { club in
                            let selected = club.clubId == activeClubId
                            Button {
                                onSelectClub?(club.clubId)
                            } label: {
                                Text(club.shortName)
                                    .font(.caption)
                                    .lineLimit(1)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(selected ? StaffColors.primary.opacity(0.18) : StaffColors.primary.opacity(0.08))
                                    .foregroundStyle(StaffColors.primary)
                                    .clipShape(Capsule())
                                    .overlay(
                                        Capsule()
                                            .stroke(selected ? StaffColors.primary : Color.clear, lineWidth: 1.5)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
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
        .onChange(of: activeClubId) { _, _ in restartRotation() }
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
    var hallLabel: String? = nil
    var paidRentalClubs: [RentalClubOption] = []
    var activeClubId: Int? = nil
    var onSelectClub: ((Int) -> Void)? = nil
    let onBack: () -> Void

    var body: some View {
        VStack {
            StaffEntryQrCard(
                staffUserId: staffUserId,
                rentalActive: rentalActive,
                blockedMessage: blockedMessage,
                entryQrFormat: entryQrFormat,
                hallLabel: hallLabel,
                paidRentalClubs: paidRentalClubs,
                activeClubId: activeClubId,
                onSelectClub: onSelectClub
            )
            .padding(24)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(StaffColors.background)
        .navigationTitle("Проход в зал")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(.white)
                }
            }
        }
        .staffToolbarStyle()
    }
}
