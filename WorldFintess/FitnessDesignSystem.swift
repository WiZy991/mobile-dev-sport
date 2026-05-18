import SwiftUI

// MARK: - Buttons (`ModernButton.kt`)

struct FCPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

struct FCPrimaryButton: View {
    let title: String
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView()
                        .tint(Theme.onPrimary)
                } else {
                    Text(title)
                        .font(FCTypography.titleMedium())
                }
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: 56)
            .foregroundStyle(Theme.onPrimary)
            .background(Theme.primary)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius22, style: .continuous))
            .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(FCPrimaryButtonStyle())
        .disabled(isLoading)
    }
}

struct FCSecondaryButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(FCTypography.labelLarge())
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
                .padding(.vertical, 14)
                .foregroundStyle(Theme.primary)
                .background(Theme.primary.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: Theme.radius22, style: .continuous))
        }
        .buttonStyle(FCPrimaryButtonStyle())
    }
}

// MARK: - Cards (`ModernCard.kt`: 2dp elevation, 18dp radius, 16 padding)

struct FCModernCard<Content: View>: View {
    var onTap: (() -> Void)?
    @ViewBuilder let content: () -> Content

    var body: some View {
        let card = content()
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius18, style: .continuous))
            .shadow(color: Color.black.opacity(0.08), radius: 3, x: 0, y: 1)

        if let onTap {
            Button(action: onTap) {
                card.scaleEffect(1)
            }
            .buttonStyle(.plain)
        } else {
            card
        }
    }
}

// MARK: - Occupancy ring (`OccupancyCard.kt`)

struct FCOccupancyRing: View {
    var percentage: Int = 0
    var status: String = ""
    let lineWidth: CGFloat

    init(percentage: Int, status: String, lineWidth: CGFloat = 8) {
        self.percentage = percentage
        self.status = status
        self.lineWidth = lineWidth
    }

    init(lineWidth: CGFloat = 8) {
        self.percentage = 0
        self.status = ""
        self.lineWidth = lineWidth
    }

    private var progressColor: Color {
        switch status {
        case "low": return Theme.accentBlue
        case "high": return Theme.accentOrange
        default: return Theme.primary
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .stroke(Theme.onSurface.opacity(0.12), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: CGFloat(min(max(percentage, 0), 100)) / 100)
                .stroke(progressColor, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
    }
}
