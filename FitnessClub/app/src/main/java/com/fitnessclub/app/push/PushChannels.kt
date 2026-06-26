package com.fitnessclub.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Каналы уведомлений приложения. */
object PushChannels {
    const val GENERAL = "general"
    const val ACCESS_ALARM = "access_alarm"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                "Уведомления",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ACCESS_ALARM,
                "Контроль прохода",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Проход по вашему QR (в т.ч. проход вдвоём)"
            }
        )
    }
}
