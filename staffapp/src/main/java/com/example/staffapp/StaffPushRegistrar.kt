package com.example.staffapp

import android.content.Context
import kotlin.concurrent.thread

object StaffPushRegistrar {
    fun registerIfLoggedIn(context: Context) {
        val store = StaffSessionStore(context)
        val session = store.loadSession() ?: return
        val apiClient = StaffApiClient(StaffApiUrl.resolve(context))
        val token = store.getOrCreatePushToken()
        thread {
            try {
                apiClient.registerPushToken(session.accessToken, token, "android")
            } catch (_: Exception) {
                // Тихая регистрация — без FCM ключей сервер всё равно сохранит токен для будущего push.
            }
        }
    }
}
