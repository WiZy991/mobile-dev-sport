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

    @Volatile
    private var lastFetchAtMs: Long = 0L

    suspend fun refreshAccessStatus(force: Boolean = false): ApiResult<AccessStatus> {
        val now = System.currentTimeMillis()
        if (!force && lastFetchAtMs > 0L && now - lastFetchAtMs < CACHE_TTL_MS) {
            return ApiResult.Success(_accessStatus.value)
        }
        return try {
            val response = api.getAccessStatus()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                _accessStatus.value = status
                lastFetchAtMs = System.currentTimeMillis()
                ApiResult.Success(status)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки статуса", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 3_000L
    }
}
