package com.fitnessclub.app.data.config

/**
 * Конфигурация приложения и внешние ссылки.
 * [SITE_URL] — платформа WorldCashFit (помощь, API).
 * Правовые документы клуба Доброзал — на [CLUB_SITE_URL].
 */
object AppConfig {
    /** Публичный сайт платформы */
    const val SITE_URL = "https://worldcashfit.ru"

    /** Сайт клуба Доброзал (правовые PDF) */
    const val CLUB_SITE_URL = "https://dobrozal.ru"

    const val DOBROZAL_DOC_URL = "$CLUB_SITE_URL/doc"
    const val DOBROZAL_OFFER_URL = "$CLUB_SITE_URL/doc/offer"
    const val DOBROZAL_PRIVACY_URL = "$CLUB_SITE_URL/doc/privacy"
    const val DOBROZAL_CONSENT_URL = "$CLUB_SITE_URL/consent_user"

    /** Ссылка на страницу помощи (FAQ, инструкции) */
    const val HELP_URL = "${SITE_URL}/help"

    /** Ссылка на восстановление пароля */
    const val FORGOT_PASSWORD_URL = "${SITE_URL}/forgot-password"

    /** Договор-оферта / пользовательское соглашение (PDF на сайте клуба) */
    const val TERMS_URL = DOBROZAL_OFFER_URL
    const val USER_AGREEMENT_URL = DOBROZAL_OFFER_URL

    /** Политика конфиденциальности */
    const val PRIVACY_URL = DOBROZAL_PRIVACY_URL

    /** Договор с клиентом и тренером — единая публичная оферта */
    const val CLIENT_AGREEMENT_URL = DOBROZAL_OFFER_URL
    const val TRAINER_AGREEMENT_URL = DOBROZAL_OFFER_URL

    /** Согласие на обработку персональных данных */
    const val PERSONAL_DATA_CONSENT_URL = DOBROZAL_CONSENT_URL

    /** Все правовые документы */
    const val LEGAL_INDEX_URL = DOBROZAL_DOC_URL

    /** Реквизиты ИП (экран в приложении) */
    const val REQUISITES_URL = "${SITE_URL}/requisites"
    
    /** Ссылка на страницу приложения в Google Play */
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=ru.worldcashfit.app"

    /** Страница приложения в RuStore (откроет приложение RuStore, если установлено) */
    const val RUSTORE_CATALOG_URL = "https://www.rustore.ru/catalog/app/ru.worldcashfit.app"
}
