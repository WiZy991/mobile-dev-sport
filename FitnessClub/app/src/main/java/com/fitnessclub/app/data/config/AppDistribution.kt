package com.fitnessclub.app.data.config

import android.content.Context
import android.os.Build

/**
 * Кнопки оценки и обновления зависят от flavor:
 * playDist — Google Play и RuStore, ruStore — только RuStore.
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

    fun canOpenStoreRating(context: Context): Boolean =
        storeRatingOptions(context).isNotEmpty()

    fun storeListingUrl(context: Context): String? =
        storeRatingOptions(context).firstOrNull()?.url

    fun rateAppButtonLabel(context: Context): String =
        storeRatingOptions(context).firstOrNull()?.label ?: "Оценить приложение"

    fun storeRatingHint(context: Context): String = StoreListing.ratingHint()

    fun storeRatingOptions(context: Context): List<StoreRatingOption> =
        StoreListing.ratingOptions(context.packageName, AppConfig.RUSTORE_CATALOG_URL)

    fun updateStoreOptions(context: Context): List<StoreRatingOption> {
        val options = StoreListing.updateOptions(context.packageName, AppConfig.RUSTORE_CATALOG_URL)
        if (options.size <= 1) return options
        val rustore = options.filter { it.url.contains("rustore.ru", ignoreCase = true) }
        val other = options.filterNot { it.url.contains("rustore.ru", ignoreCase = true) }
        return if (detectStore(context) == Store.RUSTORE) rustore + other else other + rustore
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
