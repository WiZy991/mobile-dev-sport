package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiNotification
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val api: FitnessApi,
) {

    fun getNotifications(): Flow<ApiResult<List<ApiNotification>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getNotifications()
            if (response.isSuccessful) {
                emit(ApiResult.Success(response.body() ?: emptyList()))
            } else {
                emit(
                    ApiResult.Error(
                        message = response.message().ifBlank { "Не удалось загрузить уведомления" },
                        code = response.code(),
                    )
                )
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Не удалось загрузить уведомления"))
        }
    }

    suspend fun markAsRead(notificationId: String): ApiResult<Unit> {
        return try {
            val response = api.markNotificationRead(notificationId)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(
                    message = response.message().ifBlank { "Не удалось отметить уведомление" },
                    code = response.code(),
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Не удалось отметить уведомление")
        }
    }

    suspend fun markAllAsRead(): ApiResult<Unit> {
        return try {
            val response = api.markAllNotificationsRead()
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(
                    message = response.message().ifBlank { "Не удалось отметить уведомления" },
                    code = response.code(),
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Не удалось отметить уведомления")
        }
    }
}
