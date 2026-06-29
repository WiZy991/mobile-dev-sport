package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.PromoCodeRequest
import com.fitnessclub.app.data.api.PromoValidationResponse
import com.fitnessclub.app.data.api.SubscriptionPaymentInitResponse
import com.fitnessclub.app.data.api.SubscriptionPaymentQuoteResponse
import com.fitnessclub.app.data.catalog.LocalSubscriptionCatalog
import com.fitnessclub.app.data.model.Subscription
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class PromoValidationResult(
    val isValid: Boolean,
    val code: String? = null,
    val discountPercent: Double? = null,
    val discountAmount: Double? = null,
    val error: String? = null,
)

@Singleton
class SubscriptionRepository @Inject constructor(
    private val api: FitnessApi,
    private val gson: Gson,
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
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Success(LocalSubscriptionCatalog.PLANS))
            }
        } catch (e: Exception) {
            emit(ApiResult.Success(LocalSubscriptionCatalog.PLANS))
        }
    }
    
    suspend fun getSubscriptionPlansSuspend(): ApiResult<List<SubscriptionPlan>> {
        return try {
            val response = api.getSubscriptionPlans()
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Success(LocalSubscriptionCatalog.PLANS)
            }
        } catch (e: Exception) {
            ApiResult.Success(LocalSubscriptionCatalog.PLANS)
        }
    }

    suspend fun validatePromoCode(code: String): PromoValidationResult {
        return try {
            val response = api.validatePromoCode(PromoCodeRequest(promoCode = code))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.promoValid) {
                    PromoValidationResult(
                        isValid = true,
                        code = body.promoCode ?: code.uppercase(),
                        discountPercent = body.discountPercent,
                        discountAmount = body.discountAmount,
                    )
                } else {
                    PromoValidationResult(
                        isValid = false,
                        error = body.promoError ?: "Промокод недействителен",
                    )
                }
            } else {
                PromoValidationResult(isValid = false, error = "Не удалось проверить промокод")
            }
        } catch (e: Exception) {
            PromoValidationResult(isValid = false, error = e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun quoteSubscriptionPayment(planId: String, promoCode: String?): ApiResult<SubscriptionPaymentQuoteResponse> {
        return try {
            val response = api.quoteSubscriptionPayment(
                com.fitnessclub.app.data.api.PurchaseSubscriptionRequest(
                    planId = planId,
                    promoCode = promoCode,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка расчёта цены", response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    suspend fun purchaseSubscription(planId: String, promoCode: String? = null): PurchaseSubscriptionOutcome {
        return try {
            val response = api.initSubscriptionPayment(
                com.fitnessclub.app.data.api.PurchaseSubscriptionRequest(
                    planId = planId,
                    promoCode = promoCode,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val paymentUrl = body.paymentUrl
                if (!paymentUrl.isNullOrBlank()) {
                    PurchaseSubscriptionOutcome.PaymentRequired(
                        paymentId = body.paymentId,
                        paymentUrl = paymentUrl,
                        amount = body.finalPrice.takeIf { it > 0 } ?: body.amount,
                    )
                } else {
                    PurchaseSubscriptionOutcome.Error("Не получен URL оплаты")
                }
            } else {
                val raw = response.errorBody()?.string().orEmpty()
                val parsed = runCatching {
                    gson.fromJson(raw, SubscriptionPurchaseErrorBody::class.java)
                }.getOrNull()
                when {
                    response.code() == 403 &&
                        parsed?.code == "verification_required" &&
                        !parsed.authorizeUrl.isNullOrBlank() -> {
                        PurchaseSubscriptionOutcome.VerificationRequired(
                            authorizeUrl = parsed.authorizeUrl,
                            message = parsed.message
                                ?: "Требуется верификация через Сбер ID. После успеха вернитесь и нажмите «Купить» снова.",
                        )
                    }
                    else -> {
                        val msg = humanizeApiError(
                            httpCode = response.code(),
                            raw = raw,
                            parsedMessage = parsed?.message,
                            parsedError = parsed?.error,
                            fallback = response.message() ?: "Ошибка инициализации оплаты",
                        )
                        PurchaseSubscriptionOutcome.Error(msg, response.code())
                    }
                }
            }
        } catch (e: Exception) {
            PurchaseSubscriptionOutcome.Error(e.message ?: "Неизвестная ошибка", null)
        }
    }

    suspend fun getPaymentStatus(paymentId: Int): ApiResult<SubscriptionPaymentInitResponse> {
        return try {
            val response = api.getPaymentStatus(paymentId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(response.message() ?: "Ошибка статуса оплаты", response.code())
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

    fun cancelSubscription(subscriptionId: String): Flow<ApiResult<Subscription>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.cancelSubscription(subscriptionId)
            if (response.isSuccessful && response.body() != null) {
                emit(ApiResult.Success(response.body()!!))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка отмены абонемента", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
}

private data class SubscriptionPurchaseErrorBody(
    @SerializedName("code") val code: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("authorize_url") val authorizeUrl: String? = null,
)

private fun humanizeApiError(
    httpCode: Int?,
    raw: String,
    parsedMessage: String?,
    parsedError: String?,
    fallback: String,
): String {
    parsedMessage?.takeIf { it.isNotBlank() }?.let { return it }
    parsedError?.takeIf { it.isNotBlank() }?.let { return it }

    if (raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
        return when (httpCode) {
            404 -> "Сервис оплаты на сервере не настроен (404). Обратитесь в поддержку клуба."
            502, 503 -> "Сервер временно недоступен. Попробуйте позже."
            else -> "Ошибка сервера (${httpCode ?: "?"}). Попробуйте позже или обратитесь в клуб."
        }
    }

    return raw.takeIf { it.isNotBlank() } ?: fallback
}
