package com.fitnessclub.app.data.config

import com.fitnessclub.app.BuildConfig

/** Внешние ссылки зависят от product flavor (Доброзал / Академия Борьбы). */
object AppConfig {
    val SITE_URL: String get() = BuildConfig.SITE_URL.trimEnd('/')
    val CLUB_SITE_URL: String get() = BuildConfig.CLUB_SITE_URL.trimEnd('/')

    val DOBROZAL_DOC_URL get() = "$CLUB_SITE_URL/doc"
    val DOBROZAL_OFFER_URL get() = "$CLUB_SITE_URL/doc/offer"
    val DOBROZAL_PRIVACY_URL get() = "$CLUB_SITE_URL/doc/privacy"
    val DOBROZAL_CONSENT_URL get() = "$CLUB_SITE_URL/consent_user"

    val HELP_URL get() = "$SITE_URL/help"
    val FORGOT_PASSWORD_URL get() = "$SITE_URL/forgot-password"
    val TERMS_URL get() = DOBROZAL_OFFER_URL
    val USER_AGREEMENT_URL get() = DOBROZAL_OFFER_URL
    val PRIVACY_URL get() = DOBROZAL_PRIVACY_URL
    val CLIENT_AGREEMENT_URL get() = DOBROZAL_OFFER_URL
    val TRAINER_AGREEMENT_URL get() = DOBROZAL_OFFER_URL
    val PERSONAL_DATA_CONSENT_URL get() = DOBROZAL_CONSENT_URL
    val LEGAL_INDEX_URL get() = DOBROZAL_DOC_URL
    val REQUISITES_URL get() = "$SITE_URL/requisites"

    val PLAY_STORE_URL: String get() = BuildConfig.PLAY_STORE_URL
    val RUSTORE_CATALOG_URL: String get() = BuildConfig.RUSTORE_CATALOG_URL
}
