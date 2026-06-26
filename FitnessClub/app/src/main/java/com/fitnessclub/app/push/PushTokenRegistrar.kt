package com.fitnessclub.app.push

import android.util.Log
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.PushTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Получает текущий FCM-токен и регистрирует его в CRM (`POST /api/v1/user/push-token`).
 * Авторизация добавляется OkHttp-интерсептором, поэтому вызывать после входа.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val api: FitnessApi,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun register() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Не удалось получить FCM-токен", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            if (token.isNullOrEmpty()) {
                return@addOnCompleteListener
            }
            registerToken(token)
        }
    }

    fun registerToken(token: String) {
        scope.launch {
            runCatching {
                api.registerPushToken(PushTokenRequest(token = token, platform = "android"))
            }.onFailure { Log.w(TAG, "Регистрация push-токена не удалась", it) }
        }
    }

    private companion object {
        const val TAG = "FC_Push"
    }
}
