package com.fitnessclub.app.data.config

/** Правовые документы — открываются внутри приложения (WebView), не во внешнем браузере. */
enum class LegalDocumentType(val title: String, val url: String) {
    REQUISITES("Реквизиты", AppConfig.REQUISITES_URL),
    TERMS("Договор-оферта", AppConfig.TERMS_URL),
    PRIVACY("Политика конфиденциальности", AppConfig.PRIVACY_URL),
    CLIENT_AGREEMENT("Договор с клиентом", AppConfig.CLIENT_AGREEMENT_URL),
    TRAINER_AGREEMENT("Договор с тренером", AppConfig.TRAINER_AGREEMENT_URL),
    PERSONAL_DATA_CONSENT("Согласие на обработку персональных данных", AppConfig.PERSONAL_DATA_CONSENT_URL);

    companion object {
        fun fromRouteArg(value: String): LegalDocumentType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
