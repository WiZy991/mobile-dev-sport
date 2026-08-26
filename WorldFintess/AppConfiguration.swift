import Foundation

/// Соответствует `AppConfig` и базовому URL из Android `AppModule` (`http://10.0.2.2:8000` на эмуляторе = `127.0.0.1` на симуляторе iOS).
enum AppConfiguration {
    /// Отображаемое название приложения (иконка на домашнем экране, заголовки, шаринг).
    static let appDisplayName = "Доброзал"

    /// `MARKETING_VERSION` из Xcode (`CFBundleShortVersionString`).
    static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"
    }

    /// `CURRENT_PROJECT_VERSION` из Xcode (`CFBundleVersion`).
    static var appBuild: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
    }

    /// Числовой build для сравнения с `ios_min_version_code` (как Android `versionCode`).
    static var appBuildNumber: Int {
        Int(appBuild) ?? 0
    }

    static var versionLabel: String {
        let build = appBuild
        return build.isEmpty ? appVersion : "\(appVersion) (\(build))"
    }

    /// Публичный сайт (юридические страницы, помощь).
    static let siteURL = URL(string: "https://worldcashfit.ru")!

    /// Публичный API: схема должна совпадать с тем, как открыт сайт (для Сбер ID redirect_uri нужен HTTPS).
    static let apiBaseURLString = "https://worldcashfit.ru/api/v1"

    /// Совпадает с `SBER_ID_NATIVE_REDIRECT_URI` на бэкенде (HTTPS из кабинета Сбер ID).
    /// После входа сервер делает 302 на `worldfitness://…` (`SBER_ID_NATIVE_APP_BRIDGE_URI`).
    static let sberRedirectURI = "https://worldcashfit.ru/api/v1/auth/sber/callback"

    /// Схема для завершения `ASWebAuthenticationSession` после серверного редиректа (без `://…`).
    static let sberURLScheme = "worldfitness"

    static let helpURL = siteURL.appending(path: "help")
    static let forgotPasswordURL = siteURL.appending(path: "forgot-password")
    /// Договор-оферта / пользовательское соглашение
    static let termsURL = URL(string: "https://worldcashfit.ru/license_agreement/")!
    static let privacyURL = siteURL.appending(path: "privacy")
    static let clientAgreementURL = siteURL.appending(path: "client-agreement")
    static let trainerAgreementURL = siteURL.appending(path: "trainer-agreement")
    static let personalDataConsentURL = siteURL.appending(path: "personal-data-consent")
    static let legalIndexURL = siteURL.appending(path: "legal")
    static let requisitesURL = siteURL.appending(path: "requisites")

    static let playStoreURL = URL(string: "https://play.google.com/store/apps/details?id=ru.worldcashfit.app")!
    static let appStoreURL = URL(string: "https://apps.apple.com/us/app/%D0%B4%D0%BE%D0%B1%D1%80%D0%BE%D0%B7%D0%B0%D0%BB/id6773587045")!
}
