package com.example.staffapp

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.concurrent.thread

object StaffPushRegistrar {
    /** Запрашивает реальный FCM-токен и регистрирует его в CRM (если выполнен вход). */
    fun registerIfLoggedIn(context: Context) {
        val appContext = context.applicationContext
        if (StaffSessionStore(appContext).loadSession() == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> registerToken(appContext, token) }
    }

    /** Отправляет конкретный FCM-токен в CRM (вызывается также из onNewToken). */
    fun registerToken(context: Context, fcmToken: String) {
        if (fcmToken.isBlank()) return
        val appContext = context.applicationContext
        val session = StaffSessionStore(appContext).loadSession() ?: return
        val apiClient = StaffApiClient(StaffApiUrl.resolve(appContext))
        thread {
            try {
                apiClient.registerPushToken(session.accessToken, fcmToken, "android")
            } catch (_: Exception) {
                // Тихая регистрация: сеть может быть недоступна, попробуем при следующем входе/токене.
            }
        }
    }
}
