package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.ClubInfo
import com.fitnessclub.app.data.api.FitnessApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClubRepository @Inject constructor(
    private val api: FitnessApi
) {
    suspend fun getClubInfo(): ApiResult<ClubInfo> {
        return try {
            val response = api.getClubInfo()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки информации", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun getClubs(): ApiResult<List<com.fitnessclub.app.data.api.ClubItem>> {
        return try {
            val response = api.getClubs()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun getClubDetails(clubId: String): ApiResult<ClubInfo> {
        return try {
            val response = api.getClubDetails(clubId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
}
