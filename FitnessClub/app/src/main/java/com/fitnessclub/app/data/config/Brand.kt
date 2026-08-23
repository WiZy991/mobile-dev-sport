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
