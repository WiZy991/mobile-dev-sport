package com.fitnessclub.app.data.config

/**
 * Конфигурация приложения и внешние ссылки.
 * [SITE_URL] — публичный сайт (юридические страницы, помощь); смените при другом домене.
 */
object AppConfig {
    const val APP_VERSION = "1.0.0"

    /** Публичный сайт приложения (совпадает с доменом реферальных ссылок и т.п.) */
    const val SITE_URL = "https://fitnessclub.app"

    /** Ссылка на страницу помощи (FAQ, инструкции) */
    const val HELP_URL = "${SITE_URL}/help"

    /** Ссылка на восстановление пароля */
    const val FORGOT_PASSWORD_URL = "${SITE_URL}/forgot-password"

    /** Пользовательское соглашение */
    const val TERMS_URL = "${SITE_URL}/terms"

    /** Политика конфиденциальности */
    const val PRIVACY_URL = "${SITE_URL}/privacy"
    
    /** Ссылка на страницу приложения в Google Play */
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.fitnessclub.app"
}
