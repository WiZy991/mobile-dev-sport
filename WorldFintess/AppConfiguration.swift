import Foundation

/// Соответствует `AppConfig` и базовому URL из Android `AppModule` (`http://10.0.2.2:8000` на эмуляторе = `127.0.0.1` на симуляторе iOS).
enum AppConfiguration {
    static let appVersion = "1.0.0"

    /// Для симулятора: бэкенд на той же машине. На реальном iPhone укажите LAN-IP компьютера (например `http://192.168.1.10:8000/api/v1/`).
    static let apiBaseURLString = "http://worldcashfit.ru/api/v1"

    /// Должен совпадать с `SBER_ID_NATIVE_REDIRECT_URI` на Symfony и с redirect URI в кабинете Сбер ID.
    static let sberRedirectURI = "worldfitness://auth/callback"

    /// Схема для `ASWebAuthenticationSession` (без `://…`).
    static let sberURLScheme = "worldfitness"

    static let helpURL = URL(string: "https://fitnessclub.example.com/help")!
    static let forgotPasswordURL = URL(string: "https://fitnessclub.example.com/forgot-password")!
    static let termsURL = URL(string: "https://fitnessclub.example.com/terms")!
    static let privacyURL = URL(string: "https://fitnessclub.example.com/privacy")!
    static let playStoreURL = URL(string: "https://play.google.com/store/apps/details?id=com.fitnessclub.app")!
    /// Ссылка на App Store (замените на реальный идентификатор приложения при публикации).
    static let appStoreURL = URL(string: "https://apps.apple.com/app/id0000000000")!
}
