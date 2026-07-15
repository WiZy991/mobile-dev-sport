package com.fitnessclub.app.data.config

/**
 * Конфигурация приложения и внешние ссылки.
 * [SITE_URL] — публичный сайт (юридические страницы, помощь); смените при другом домене.
 */
object AppConfig {
    /** Публичный сайт приложения (совпадает с доменом реферальных ссылок и т.п.) */
    const val SITE_URL = "https://worldcashfit.ru"

    /** Ссылка на страницу помощи (FAQ, инструкции) */
    const val HELP_URL = "${SITE_URL}/help"

    /** Ссылка на восстановление пароля */
    const val FORGOT_PASSWORD_URL = "${SITE_URL}/forgot-password"

    /** Договор-оферта / пользовательское соглашение */
    const val TERMS_URL = "${SITE_URL}/license_agreement/"
    const val USER_AGREEMENT_URL = "${SITE_URL}/user_agreement/"

    /** Политика конфиденциальности */
    const val PRIVACY_URL = "${SITE_URL}/privacy/"

    /** Договор с клиентом */
    const val CLIENT_AGREEMENT_URL = "${SITE_URL}/client-agreement"

    /** Договор с тренером */
    const val TRAINER_AGREEMENT_URL = "${SITE_URL}/trainer-agreement"

    /** Согласие на обработку персональных данных */
    const val PERSONAL_DATA_CONSENT_URL = "${SITE_URL}/consent_user"

    /** Все правовые документы */
    const val LEGAL_INDEX_URL = "${SITE_URL}/legal"

    /** Реквизиты ИП (для онлайн-оплаты в приложении) */
    const val REQUISITES_URL = "${SITE_URL}/requisites"
    
    /** Ссылка на страницу приложения в Google Play */
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=ru.worldcashfit.app"

    /** Страница приложения в RuStore (откроет приложение RuStore, если установлено) */
    const val RUSTORE_CATALOG_URL = "https://www.rustore.ru/catalog/app/ru.worldcashfit.app"
}
