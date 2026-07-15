package com.fitnessclub.app.data.config

import android.content.Context
import android.os.Build

/**
 * Определяет, из какого магазина установлено приложение, чтобы открывать
 * страницу оценки в том же магазине (RuStore vs Google Play).
 */
object AppDistribution {
    data class StoreRatingOption(
        val label: String,
        val url: String,
    )

    enum class Store {
        GOOGLE_PLAY,
        RUSTORE,
        OTHER,
    }

    fun detectStore(context: Context): Store =
        resolveStoreFromInstaller(installerPackageName(context))

    fun isInstalledFromRuStore(context: Context): Boolean =
        detectStore(context) == Store.RUSTORE

    fun isInstalledFromGooglePlay(context: Context): Boolean =
        detectStore(context) == Store.GOOGLE_PLAY

    /** @deprecated Используйте [storeRatingOptions]. */
    fun canOpenStoreRating(context: Context): Boolean =
        storeRatingOptions(context).isNotEmpty()

    /** @deprecated Используйте [storeRatingOptions]. */
    fun storeListingUrl(context: Context): String? =
        storeRatingOptions(context).firstOrNull()?.url

    /** @deprecated Используйте [storeRatingOptions]. */
    fun rateAppButtonLabel(context: Context): String =
        storeRatingOptions(context).firstOrNull()?.label ?: "Оценить приложение"

    fun storeRatingHint(context: Context): String? = when (detectStore(context)) {
        Store.GOOGLE_PLAY -> "Оставьте отзыв в Google Play — это помогает развивать приложение."
        Store.RUSTORE -> "Оставьте отзыв в RuStore — это помогает развивать приложение."
        Store.OTHER -> "Выберите магазин, из которого вы установили приложение:"
    }

    fun storeRatingOptions(context: Context): List<StoreRatingOption> = when (detectStore(context)) {
        Store.GOOGLE_PLAY -> listOf(
            StoreRatingOption(
                label = "Оценить в Google Play",
                url = AppConfig.PLAY_STORE_URL,
            ),
        )
        Store.RUSTORE -> listOf(
            StoreRatingOption(
                label = "Оценить в RuStore",
                url = AppConfig.RUSTORE_CATALOG_URL,
            ),
        )
        Store.OTHER -> listOf(
            StoreRatingOption(
                label = "Оценить в Google Play",
                url = AppConfig.PLAY_STORE_URL,
            ),
            StoreRatingOption(
                label = "Оценить в RuStore",
                url = AppConfig.RUSTORE_CATALOG_URL,
            ),
        )
    }

    private fun resolveStoreFromInstaller(installer: String?): Store {
        val lower = installer?.lowercase().orEmpty()
        return when {
            lower == "com.android.vending" -> Store.GOOGLE_PLAY
            lower == "ru.vk.store" || lower.contains("rustore") -> Store.RUSTORE
            else -> Store.OTHER
        }
    }

    private fun installerPackageName(context: Context): String? {
        val pm = context.packageManager
        val packageName = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(packageName)
                sequenceOf(
                    info.installingPackageName,
                    info.initiatingPackageName,
                    info.originatingPackageName,
                ).firstOrNull { resolveStoreFromInstaller(it) != Store.OTHER }
                    ?: info.installingPackageName
                    ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
