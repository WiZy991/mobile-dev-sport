package com.fitnessclub.app.data.config

/** Встроенные PDF правовых документов (assets/legal/). */
enum class LegalPdfAsset(
    val assetPath: String,
    val cacheFileName: String,
    val title: String,
) {
    DOBROZAL_OFFER(
        assetPath = "legal/dobrozal_offer.pdf",
        cacheFileName = "dobrozal_offer.pdf",
        title = "Публичная оферта",
    ),
    PRIVACY_POLICY(
        assetPath = "legal/privacy_policy.pdf",
        cacheFileName = "privacy_policy.pdf",
        title = "Политика конфиденциальности",
    ),
    CONSENT_USER(
        assetPath = "legal/consent_user.pdf",
        cacheFileName = "consent_user.pdf",
        title = "Согласие на обработку персональных данных",
    ),
    USER_AGREEMENT(
        assetPath = "legal/user_agreement.pdf",
        cacheFileName = "user_agreement.pdf",
        title = "Пользовательское соглашение",
    ),
    WCF_CLUB_OFFER(
        assetPath = "legal/wcf_club_offer.pdf",
        cacheFileName = "wcf_club_offer.pdf",
        title = "Оферта для клубов",
    );

    companion object {
        fun fromAnnotation(value: String): LegalPdfAsset? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
