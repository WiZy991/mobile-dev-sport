package com.fitnessclub.app.data.config

import com.fitnessclub.app.BuildConfig

/**
 * Бренд текущего product flavor.
 *
 * У white-label APK (не Доброзал) имя сети всегда из [BuildConfig.BRAND_NAME],
 * даже если CRM пока отдаёт «Доброзал» — иначе на экране входа чужой бренд.
 */
object Brand {
    val name: String get() = BuildConfig.BRAND_NAME

    /** Отдельное приложение франшизы (свой applicationId / Play listing). */
    val isWhiteLabel: Boolean
        get() = BuildConfig.APPLICATION_ID != "ru.worldcashfit.app"

    val phoneLoginEnabled: Boolean get() = BuildConfig.PHONE_LOGIN_ENABLED
    val sberLoginEnabled: Boolean get() = BuildConfig.SBER_LOGIN_ENABLED
    val onlinePaymentEnabled: Boolean get() = BuildConfig.ONLINE_PAYMENT_ENABLED

    /** alfa — Доброзал (браузер), yookassa — Академия борьбы (мобильный SDK). */
    val paymentProvider: String get() = BuildConfig.PAYMENT_PROVIDER

    val usesYooKassa: Boolean get() = paymentProvider == "yookassa"

    fun yooKassaSetupError(): String? {
        if (!usesYooKassa) return null
        if (BuildConfig.YOOKASSA_SHOP_ID.isBlank() || BuildConfig.YOOKASSA_CLIENT_KEY.isBlank()) {
            return "Оплата ЮKassa не настроена. Добавьте YOOKASSA_SHOP_ID и YOOKASSA_CLIENT_KEY в local.properties и пересоберите приложение."
        }
        return null
    }

    /** Продавец для покупок в приложении; пусто — не показывать. */
    val legalEntityLine: String get() = BuildConfig.LEGAL_ENTITY_LINE

    fun orFallback(fromApi: String?): String {
        if (isWhiteLabel) {
            return BuildConfig.BRAND_NAME
        }
        val n = fromApi?.trim().orEmpty()
        return when {
            n.isBlank() -> BuildConfig.BRAND_NAME
            n.equals("FitnessClub", ignoreCase = true) -> BuildConfig.BRAND_NAME
            else -> n
        }
    }
}
