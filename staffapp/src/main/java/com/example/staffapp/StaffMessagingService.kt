package com.example.staffapp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Приём push от FCM в приложении сотрудника: показ уведомления и регистрация обновлённого токена. */
class StaffMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        StaffPushRegistrar.registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"].orEmpty()
        val notification = message.notification
        val title = notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = notification?.body ?: data["body"] ?: ""

        when (type) {
            "access_alarm" -> StaffNotificationHelper.showAccessAlarm(this, title, body)
            else -> StaffNotificationHelper.showSupportNotification(this, title, body)
        }
    }
}
