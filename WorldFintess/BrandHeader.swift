import SwiftUI

/// Аналог Android `BrandHeader.kt` — логотип + бренд сети (не название площадки).
struct BrandHeader: View {
    var brandName: String = AppConfiguration.appDisplayName
    var subtitle: String? = nil
    var textColor: Color = .white
    var showLogo: Bool = true
    var logoSize: CGFloat = 44
    /// @deprecated Используйте `brandName`.
    var clubName: String? = nil

    private var title: String {
        let brand = brandName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !brand.isEmpty { return brand }
        let legacy = clubName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !legacy.isEmpty { return legacy }
        return AppConfiguration.appDisplayName
    }

    var body: some View {
        VStack(spacing: 6) {
            if showLogo {
                HStack(spacing: 10) {
                    Image("BrandLogo")
                        .resizable()
                        .scaledToFill()
                        .frame(width: logoSize, height: logoSize)
                        .clipShape(Circle())
                    Text(title)
                        .font(.system(size: 26, weight: .bold))
                        .foregroundStyle(textColor)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
            } else {
                Text(title)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(textColor)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }

            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(FCTypography.bodyMedium())
                    .foregroundStyle(textColor.opacity(0.9))
                    .lineLimit(1)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
