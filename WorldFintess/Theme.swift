import SwiftUI
import UIKit

// MARK: - FitnessClub Android parity (`Color.kt`, `colors.xml`, Material 3 light scheme)

enum Theme {
    // Primary
    static let primary = Color(hex: 0xFF6B35)
    static let primaryVariant = Color(hex: 0xE85A2A)
    static let onPrimary = Color.white

    // Secondary
    static let secondary = Color(hex: 0x2C3E50)
    static let secondaryVariant = Color(hex: 0x1A252F)
    static let onSecondary = Color.white

    // Surfaces (адаптивные под тёмную тему, значения тёмной — `Color.kt` Dark*)
    static let background = Color(light: 0xF5F5F5, dark: 0x121212)
    static let surface = Color(light: 0xFFFFFF, dark: 0x1E1E1E)
    static let surfaceVariant = Color(light: 0xF5F5F5, dark: 0x2A2A2A)
    static let onBackground = Color(light: 0x1C1B1F, dark: 0xE1E1E1)
    static let onSurface = Color(light: 0x1C1B1F, dark: 0xE1E1E1)
    static let onSurfaceVariant = Color(light: 0x757575, dark: 0xAAAAAA)
    static let outlineVariant = Color(light: 0xE0E0E0, dark: 0x3A3A3A)

    // Status
    static let success = Color(hex: 0x4CAF50)
    static let warning = Color(hex: 0xFFC107)
    static let error = Color(hex: 0xF44336)

    // Accents
    static let accentOrange = Color(hex: 0xFF6B35)
    static let accentBlue = Color(hex: 0x3498DB)
    static let accentGreen = Color(hex: 0x27AE60)

    /// Экран входа (`LoginScreen.kt`): терракота и кнопка — не общий Primary.
    static let loginBackground = Color(hex: 0xD35400)
    static let loginButton = Color(hex: 0xB84A18)
    static let loginOnBackground = Color.white

    // Nav / top bar (status bar tint on Android = primary)
    static let navigationBar = primary

    // Radii (dp = pt) — `Shape.kt` AppShapes + screen-specific
    static let radius12: CGFloat = 12
    static let radius14: CGFloat = 14
    static let radius16: CGFloat = 16
    static let radius18: CGFloat = 18
    static let radius20: CGFloat = 20
    static let radius22: CGFloat = 22
    static let radius28: CGFloat = 28
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    /// Динамический цвет: разные значения для светлой и тёмной темы.
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        let r = CGFloat((hex >> 16) & 0xFF) / 255
        let g = CGFloat((hex >> 8) & 0xFF) / 255
        let b = CGFloat(hex & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: alpha)
    }
}

/// Режим темы приложения (`ThemeMode` на Android: System/Light/Dark).
enum AppThemeMode: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "Системная"
        case .light: return "Светлая"
        case .dark: return "Тёмная"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

// MARK: - Typography (Material 3 `Type.kt` — system font = SF Pro, Android uses Roboto)

enum FCTypography {
    static func displayMedium() -> Font { .system(size: 45, weight: .bold) }
    static func headlineLarge() -> Font { .system(size: 32, weight: .semibold) }
    static func headlineMedium() -> Font { .system(size: 28, weight: .semibold) }
    /// Material `headlineSmall`: 24 / 32 sp, SemiBold
    static func headlineSmall() -> Font { .system(size: 24, weight: .semibold) }
    static func titleLarge() -> Font { .system(size: 22, weight: .medium) }
    static func titleMedium() -> Font { .system(size: 16, weight: .medium) }
    static func titleSmall() -> Font { .system(size: 14, weight: .medium) }
    static func bodyLarge() -> Font { .system(size: 16, weight: .regular) }
    static func bodyMedium() -> Font { .system(size: 14, weight: .regular) }
    static func bodySmall() -> Font { .system(size: 12, weight: .regular) }
    static func labelLarge() -> Font { .system(size: 14, weight: .medium) }
    static func labelMedium() -> Font { .system(size: 12, weight: .medium) }
    static func labelSmall() -> Font { .system(size: 11, weight: .medium) }
}
