import SwiftUI

struct StaffSectionTitle: View {
    let title: String

    var body: some View {
        Text(title)
            .font(.title3)
            .fontWeight(.bold)
            .padding(.top, 4)
            .padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct StaffHeroCard: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(StaffColors.onPrimary)
            Text(subtitle)
                .font(.body)
                .foregroundStyle(StaffColors.onPrimary.opacity(0.9))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(StaffColors.primary)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
    }
}

struct StaffMetricsRow: View {
    let metrics: [MetricUi]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(metrics) { metric in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(metric.value)
                            .font(.title2)
                            .fontWeight(.bold)
                            .foregroundStyle(StaffColors.primary)
                        Text(metric.label)
                            .font(.caption)
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                    }
                    .frame(width: 140, alignment: .leading)
                    .padding(14)
                    .background(StaffColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
                }
            }
        }
    }
}

struct StaffChipRow: View {
    let chips: [DayChipUi]
    let onChipClick: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(chips) { chip in
                    Button {
                        onChipClick(chip.date)
                    } label: {
                        VStack(spacing: 2) {
                            Text(chip.label)
                                .font(.subheadline)
                            if chip.count >= 0 {
                                Text("\(chip.count)")
                                    .font(.caption2)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(chip.selected ? StaffColors.primary : StaffColors.surface)
                        .foregroundStyle(chip.selected ? StaffColors.onPrimary : StaffColors.onBackground)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(StaffColors.onSurfaceVariant.opacity(0.3), lineWidth: chip.selected ? 0 : 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }
}

struct StaffBadge: View {
    let text: String
    let color: BadgeColor

    var body: some View {
        Text(text)
            .font(.caption2)
            .fontWeight(.medium)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(backgroundColor)
            .foregroundStyle(foregroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var backgroundColor: Color {
        switch color {
        case .success: return StaffColors.success.opacity(0.15)
        case .warning: return StaffColors.warning.opacity(0.2)
        case .error: return StaffColors.error.opacity(0.15)
        case .primary: return StaffColors.primary.opacity(0.12)
        case .neutral: return StaffColors.onSurfaceVariant.opacity(0.12)
        }
    }

    private var foregroundColor: Color {
        switch color {
        case .success: return StaffColors.success
        case .warning: return StaffColors.warning
        case .error: return StaffColors.error
        case .primary: return StaffColors.primary
        case .neutral: return StaffColors.onSurfaceVariant
        }
    }
}

struct StaffListCard: View {
    let item: ListCardUi
    var onClick: (() -> Void)?

    var body: some View {
        Group {
            if let onClick {
                Button(action: onClick) { cardContent }
                    .buttonStyle(.plain)
            } else {
                cardContent
            }
        }
    }

    private var cardContent: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    Text(item.title)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundStyle(StaffColors.onBackground)
                    if let badge = item.badge {
                        StaffBadge(text: badge, color: item.badgeColor)
                    }
                }
                if !item.subtitle.isEmpty {
                    Text(item.subtitle)
                        .font(.body)
                        .foregroundStyle(StaffColors.onBackground)
                }
                if !item.meta.isEmpty {
                    Text(item.meta)
                        .font(.caption)
                        .foregroundStyle(StaffColors.onSurfaceVariant)
                }
            }
            Spacer(minLength: 8)
            if onClick != nil {
                Image(systemName: "chevron.right")
                    .foregroundStyle(StaffColors.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(StaffColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
    }
}

struct StaffMenuCard: View {
    let title: String
    let items: [(icon: String, title: String, hint: String)]
    let onItemClick: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title)
                .font(.title3)
                .fontWeight(.bold)
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 8)

            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                Button {
                    onItemClick(index)
                } label: {
                    HStack(spacing: 14) {
                        Image(systemName: item.icon)
                            .font(.title3)
                            .foregroundStyle(StaffColors.primary)
                            .frame(width: 44, height: 44)
                            .background(StaffColors.primary.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 12))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.body)
                                .fontWeight(.medium)
                                .foregroundStyle(StaffColors.onBackground)
                            if !item.hint.isEmpty {
                                Text(item.hint)
                                    .font(.caption)
                                    .foregroundStyle(StaffColors.onSurfaceVariant)
                            }
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundStyle(StaffColors.onSurfaceVariant)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.plain)

                if index < items.count - 1 {
                    Divider().padding(.horizontal, 16)
                }
            }
            Spacer().frame(height: 8)
        }
        .background(StaffColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
    }
}

struct StaffPrimaryButton: View {
    let text: String
    let action: () -> Void
    var enabled: Bool = true

    var body: some View {
        Button(action: action) {
            Text(text)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .foregroundStyle(StaffColors.onPrimary)
                .background(enabled ? StaffColors.primary : StaffColors.primary.opacity(0.5))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct StaffSecondaryButton: View {
    let text: String
    let action: () -> Void
    var enabled: Bool = true

    var body: some View {
        Button(action: action) {
            Text(text)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .foregroundStyle(StaffColors.primary)
                .background(StaffColors.primary.opacity(enabled ? 0.12 : 0.06))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct StaffActionButtons: View {
    let actions: [ActionUi]
    let onAction: (String) -> Void

    var body: some View {
        VStack(spacing: 8) {
            ForEach(actions) { action in
                if action.id.hasPrefix("text:") {
                    Button(action.label) { onAction(action.id) }
                        .foregroundStyle(StaffColors.primary)
                } else if action.id.hasPrefix("secondary:") {
                    StaffSecondaryButton(text: action.label) { onAction(action.id) }
                } else {
                    StaffPrimaryButton(text: action.label) { onAction(action.id) }
                }
            }
        }
    }
}

struct StaffSearchBar: View {
    let query: String
    let onQueryChange: (String) -> Void
    let onSearch: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            TextField("Имя, email или телефон", text: Binding(
                get: { query },
                set: onQueryChange
            ))
            .textFieldStyle(.roundedBorder)

            Button("Найти", action: onSearch)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(StaffColors.primary)
                .foregroundStyle(StaffColors.onPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .padding(12)
        .background(StaffColors.surface)
        .shadow(color: .black.opacity(0.06), radius: 2, y: 1)
    }
}

struct StaffLoadingState: View {
    var message: String = "Загрузка..."

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(StaffColors.primary)
            Text(message)
                .foregroundStyle(StaffColors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(48)
    }
}

struct StaffEmptyState: View {
    let message: String
    var icon: String = "tray"

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 56))
                .foregroundStyle(StaffColors.onSurfaceVariant)
            Text(message)
                .font(.body)
                .foregroundStyle(StaffColors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(40)
    }
}

struct StaffErrorState: View {
    let message: String
    var onRetry: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "exclamationmark.circle")
                    .foregroundStyle(StaffColors.error)
                Text(message)
                    .foregroundStyle(StaffColors.error)
                    .font(.body)
            }
            if let onRetry {
                Button("Повторить", action: onRetry)
                    .foregroundStyle(StaffColors.primary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(StaffColors.error.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct StaffInfoBanner: View {
    let text: String
    var color: Color = StaffColors.accentBlue

    var body: some View {
        Text(text)
            .font(.body)
            .foregroundStyle(color)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
