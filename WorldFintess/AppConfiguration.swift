import Foundation

/// Соответствует `AppConfig` и базовому URL из Android `AppModule` (`http://10.0.2.2:8000` на эмуляторе = `127.0.0.1` на симуляторе iOS).
enum AppConfiguration {
    static let appVersion = "1.0.0"

    /// Публичный API: схема должна совпадать с тем, как открыт сайт (для Сбер ID redirect_uri нужен HTTPS).
    static let apiBaseURLString = "https://worldcashfit.ru/api/v1"

    /// Совпадает с `SBER_ID_NATIVE_REDIRECT_URI` на бэкенде (HTTPS из кабинета Сбер ID).
    /// После входа сервер делает 302 на `worldfitness://…` (`SBER_ID_NATIVE_APP_BRIDGE_URI`).
    static let sberRedirectURI = "https://worldcashfit.ru/api/v1/auth/sber/callback"

    /// Схема для завершения `ASWebAuthenticationSession` после серверного редиректа (без `://…`).
    static let sberURLScheme = "worldfitness"

    static let helpURL = URL(string: "https://fitnessclub.example.com/help")!
    static let forgotPasswordURL = URL(string: "https://fitnessclub.example.com/forgot-password")!
    static let termsURL = URL(string: "https://fitnessclub.example.com/terms")!
    static let privacyURL = URL(string: "https://fitnessclub.example.com/privacy")!
    static let playStoreURL = URL(string: "https://play.google.com/store/apps/details?id=com.fitnessclub.app")!
    /// Ссылка на App Store (замените на реальный идентификатор приложения при публикации).
    static let appStoreURL = URL(string: "https://apps.apple.com/app/id0000000000")!
}
