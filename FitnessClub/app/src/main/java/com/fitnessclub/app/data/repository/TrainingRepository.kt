package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.Booking
import com.fitnessclub.app.data.model.Training
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingRepository @Inject constructor(
    private val api: FitnessApi
) {
    
    fun getTrainings(date: String? = null, type: String? = null): Flow<ApiResult<List<Training>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getTrainings(date, type)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка загрузки расписания", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun getTrainingDetails(id: String): Flow<ApiResult<Training>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getTrainingDetails(id)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка загрузки тренировки", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun getMyBookings(): Flow<ApiResult<List<Booking>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getMyBookings()
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка загрузки записей", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun bookTraining(trainingId: String): Flow<ApiResult<Booking>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.bookTraining(trainingId)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка записи", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun cancelBooking(bookingId: String): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.cancelBooking(bookingId)
            if (response.isSuccessful) {
                emit(ApiResult.Success(Unit))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка отмены записи", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun joinWaitingList(trainingId: String): Flow<ApiResult<Booking>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.joinWaitingList(trainingId)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
}
