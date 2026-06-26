package com.fitnessclub.app.data.config

/** Правовые документы — нативные экраны в приложении (текст с API, без сайта). */
enum class LegalDocumentType(val title: String, val apiSlug: String) {
    REQUISITES("Реквизиты", "requisites"),
    TERMS("Договор-оферта", "license_agreement"),
    PRIVACY("Политика конфиденциальности", "privacy"),
    CLIENT_AGREEMENT("Договор с клиентом", "client-agreement"),
    TRAINER_AGREEMENT("Договор с тренером", "trainer-agreement"),
    PERSONAL_DATA_CONSENT("Согласие на обработку персональных данных", "personal-data-consent");

    companion object {
        fun fromRouteArg(value: String): LegalDocumentType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
