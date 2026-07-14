package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.AccessStatus
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessRepository @Inject constructor(
    private val api: FitnessApi,
) {
    private val _accessStatus = MutableStateFlow(AccessStatus())
    val accessStatus: StateFlow<AccessStatus> = _accessStatus.asStateFlow()

    suspend fun refreshAccessStatus(): ApiResult<AccessStatus> {
        return try {
            val response = api.getAccessStatus()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                _accessStatus.value = status
                ApiResult.Success(status)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки статуса", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
}
