package com.example.staffapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object StaffNotificationHelper {
    private const val CHANNEL_ID = "staff_support"
    private const val ACCESS_ALARM_CHANNEL_ID = "access_alarm"
    private var notificationId = 1000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Обращения и уведомления",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Новые обращения из клиентского приложения"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ACCESS_ALARM_CHANNEL_ID,
                "Контроль прохода",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Тревоги доступа: проход вдвоём / вход группой"
            }
        )
    }

    fun showSupportNotification(context: Context, title: String, body: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(supportPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId++, notification)
    }

    fun showAccessAlarm(context: Context, title: String, body: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, ACCESS_ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(supportPendingIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId++, notification)
    }

    private fun supportPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WorkActivity::class.java).apply {
            putExtra(WorkActivity.EXTRA_INITIAL_TAB, R.id.nav_support)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
