import SwiftUI

struct RentalClubSelectCard: View {
    let club: RentalClubOption
    let selected: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(club.name.isEmpty ? "Зал" : club.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(StaffColors.onSurface)
                        .multilineTextAlignment(.leading)
                    if !club.address.isEmpty {
                        Text(club.address)
                            .font(.caption)
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                            .multilineTextAlignment(.leading)
                    }
                    Text(String(format: "%.0f ₽ / %d дн.", club.amountRub, club.days))
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                    Text(statusLabel)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(club.rentalActive ? StaffColors.success : StaffColors.onSurfaceVariant)
                }
                Spacer(minLength: 0)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? StaffColors.primary : StaffColors.onSurfaceVariant)
            }
            .padding(14)
            .background(StaffColors.surface)
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(selected ? StaffColors.primary : StaffColors.onSurfaceVariant.opacity(0.2), lineWidth: selected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var statusLabel: String {
        if club.rentalActive, let until = club.paidUntil, !until.isEmpty {
            return "Оплачен до \(String(until.prefix(10)))"
        }
        if club.rentalActive { return "Активен" }
        return "Не оплачен"
    }
}
