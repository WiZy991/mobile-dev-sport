package com.example.staffapp

import android.content.Context
import android.os.Build

object StaffApiUrl {
    fun resolve(context: Context): String {
        val emulatorUrl = context.getString(R.string.base_api_url).trim()
        if (isEmulator()) {
            return emulatorUrl
        }
        val deviceUrl = context.getString(R.string.base_api_url_device).trim()
        return deviceUrl.ifBlank { emulatorUrl }
    }

    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk", ignoreCase = true)
            || Build.MODEL.contains("Emulator", ignoreCase = true)
            || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
            || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
            || Build.HARDWARE.contains("goldfish", ignoreCase = true)
            || Build.HARDWARE.contains("ranchu", ignoreCase = true)
            || Build.PRODUCT.contains("sdk", ignoreCase = true)
    }
}
