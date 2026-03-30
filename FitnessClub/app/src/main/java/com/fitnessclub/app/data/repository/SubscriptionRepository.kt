package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.Subscription
import com.fitnessclub.app.data.model.SubscriptionPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val api: FitnessApi
) {
    
    fun getMySubscriptions(): Flow<ApiResult<List<Subscription>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getMySubscriptions()
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка загрузки абонементов", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun getSubscriptionPlans(): Flow<ApiResult<List<SubscriptionPlan>>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.getSubscriptionPlans()
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка загрузки тарифов", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    suspend fun getSubscriptionPlansSuspend(): ApiResult<List<SubscriptionPlan>> {
        return try {
            val response = api.getSubscriptionPlans()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка загрузки тарифов", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun purchaseSubscription(planId: String, promoCode: String? = null): ApiResult<Subscription> {
        return try {
            val response = api.purchaseSubscription(
                com.fitnessclub.app.data.api.PurchaseSubscriptionRequest(
                    planId = planId,
                    promoCode = promoCode
                )
            )
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                val msg = response.message() ?: response.errorBody()?.string() ?: "Ошибка покупки абонемента"
                ApiResult.Error(msg, response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    fun freezeSubscription(subscriptionId: String, days: Int): Flow<ApiResult<Subscription>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.freezeSubscription(subscriptionId, days)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка заморозки", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun unfreezeSubscription(subscriptionId: String): Flow<ApiResult<Subscription>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.unfreezeSubscription(subscriptionId)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка разморозки", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
}
