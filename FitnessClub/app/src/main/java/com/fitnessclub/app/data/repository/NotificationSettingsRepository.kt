package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.NotificationSettings
import com.fitnessclub.app.push.PushTokenRegistrar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSettingsRepository @Inject constructor(
    private val api: FitnessApi,
    private val pushTokenRegistrar: PushTokenRegistrar,
) {
    fun load(): Flow<ApiResult<NotificationSettings>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getNotificationSettings()
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error("Не удалось загрузить настройки уведомлений", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    fun save(settings: NotificationSettings): Flow<ApiResult<NotificationSettings>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.updateNotificationSettings(settings)
            if (response.isSuccessful && response.body() != null) {
                val saved = response.body()!!
                syncPushRegistration(saved.pushEnabled)
                emit(ApiResult.Success(saved))
            } else {
                emit(ApiResult.Error("Не удалось сохранить настройки", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    suspend fun syncPushRegistration(pushEnabled: Boolean) {
        if (pushEnabled) {
            pushTokenRegistrar.register()
        } else {
            runCatching { api.unregisterPushToken() }
        }
    }
}
