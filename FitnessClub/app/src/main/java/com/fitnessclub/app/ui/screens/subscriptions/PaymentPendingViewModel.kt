package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.SubscriptionPaymentInitResponse
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PaymentPendingEvent {
    data object Success : PaymentPendingEvent()
    data class Failed(val message: String) : PaymentPendingEvent()
}

@HiltViewModel
class PaymentPendingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _events = MutableSharedFlow<PaymentPendingEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var pollJob: Job? = null
    private var finished = false
    private var activePaymentId: Int? = null

    fun cancelPolling() {
        finished = true
        pollJob?.cancel()
        pollJob = null
    }

    fun pollPaymentStatus(paymentId: Int) {
        finished = false
        activePaymentId = paymentId
        startPolling(paymentId)
    }

    /** Одна проверка при возврате из банка / Custom Tabs — без перезапуска полного цикла. */
    fun resumePolling(paymentId: Int) {
        if (finished) return
        activePaymentId = paymentId
        viewModelScope.launch {
            if (checkOnce(paymentId)) {
                finished = true
                pollJob?.cancel()
            }
        }
    }

    fun refreshPaymentStatus(paymentId: Int) {
        if (finished) return
        viewModelScope.launch {
            if (checkOnce(paymentId)) {
                finished = true
                pollJob?.cancel()
            }
        }
    }

    private fun startPolling(paymentId: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val maxAttempts = 120
            repeat(maxAttempts) { attempt ->
                if (checkOnce(paymentId)) {
                    finished = true
                    return@launch
                }
                delay(if (attempt < 10) 1000L else 2000L)
            }
            finished = true
            _events.emit(
                PaymentPendingEvent.Failed(
                    "Не удалось подтвердить оплату. Если деньги списались — откройте профиль и проверьте абонемент."
                )
            )
        }
    }

    private suspend fun refreshSessionIfPossible(): Boolean {
        val refreshToken = tokenManager.getRefreshToken()?.trim().orEmpty()
        if (refreshToken.isEmpty()) return false
        return when (val result = authRepository.restoreSessionWithRefreshToken(refreshToken).first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> true
            else -> false
        }
    }

    /** @return true если обработка завершена (успех или ошибка) */
    private suspend fun checkOnce(paymentId: Int): Boolean {
        when (val result = subscriptionRepository.getPaymentStatus(paymentId)) {
            is ApiResult.Success -> return handleStatus(result.data)
            is ApiResult.Error -> {
                if (result.code == 401) {
                    if (refreshSessionIfPossible()) {
                        return when (val retry = subscriptionRepository.getPaymentStatus(paymentId)) {
                            is ApiResult.Success -> handleStatus(retry.data)
                            is ApiResult.Error -> handleAuthError(retry.code)
                            is ApiResult.Loading -> false
                        }
                    }
                    return handleAuthError(result.code)
                }
                if (result.code == 403) {
                    return handleAuthError(result.code)
                }
            }
            is ApiResult.Loading -> Unit
        }
        return false
    }

    private suspend fun handleAuthError(code: Int?): Boolean {
        if (code != 401 && code != 403) return false
        _events.emit(
            PaymentPendingEvent.Failed(
                "Сессия истекла. Войдите снова и проверьте абонемент в профиле — оплата могла уже пройти."
            )
        )
        return true
    }

    private suspend fun handleStatus(data: SubscriptionPaymentInitResponse): Boolean {
        return when (data.status) {
            "paid" -> {
                delay(300)
                _events.emit(PaymentPendingEvent.Success)
                true
            }
            "failed", "expired", "cancelled" -> {
                val reason = data.failureReason ?: when (data.status) {
                    "expired" -> "Время оплаты истекло"
                    "cancelled" -> "Оплата отменена"
                    else -> "Оплата не прошла"
                }
                _events.emit(PaymentPendingEvent.Failed(reason))
                true
            }
            else -> false
        }
    }
}
