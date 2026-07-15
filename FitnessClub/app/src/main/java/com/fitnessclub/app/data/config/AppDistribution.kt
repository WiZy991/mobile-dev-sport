package com.fitnessclub.app.data.config

import android.content.Context
import android.os.Build

/**
 * Определяет, из какого магазина установлено приложение, чтобы открывать
 * страницу оценки в том же магазине (RuStore vs Google Play).
 */
object AppDistribution {
    private const val PACKAGE_NAME = "ru.worldcashfit.app"

    enum class Store {
        GOOGLE_PLAY,
        RUSTORE,
        OTHER,
    }

    fun detectStore(context: Context): Store {
        val installer = installerPackageName(context)?.lowercase().orEmpty()
        return when {
            installer == "com.android.vending" -> Store.GOOGLE_PLAY
            installer == "ru.vk.store" || installer.contains("rustore") -> Store.RUSTORE
            else -> Store.OTHER
        }
    }

    fun storeListingUrl(context: Context): String = when (detectStore(context)) {
        Store.RUSTORE -> AppConfig.RUSTORE_CATALOG_URL
        Store.GOOGLE_PLAY -> AppConfig.PLAY_STORE_URL
        Store.OTHER -> AppConfig.RUSTORE_CATALOG_URL
    }

    fun rateAppButtonLabel(context: Context): String = when (detectStore(context)) {
        Store.RUSTORE -> "Оценить в RuStore"
        Store.GOOGLE_PLAY -> "Оценить в Google Play"
        Store.OTHER -> "Оценить приложение"
    }

    private fun installerPackageName(context: Context): String? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(PACKAGE_NAME).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(PACKAGE_NAME)
            }
        } catch (_: Exception) {
            null
        }
    }
}
