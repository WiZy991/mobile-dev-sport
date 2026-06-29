package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.SubscriptionPaymentInitResponse
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PaymentPendingEvent {
    data object Success : PaymentPendingEvent()
    data class Failed(val message: String) : PaymentPendingEvent()
}

@HiltViewModel
class PaymentPendingViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<PaymentPendingEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var pollJob: Job? = null

    fun pollPaymentStatus(paymentId: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val maxAttempts = 60
            repeat(maxAttempts) { attempt ->
                if (checkOnce(paymentId, attempt)) {
                    return@launch
                }
                delay(if (attempt < 5) 1500L else 3000L)
            }
            _events.emit(
                PaymentPendingEvent.Failed(
                    "Не удалось подтвердить оплату. Если деньги списались — проверьте абонемент в профиле."
                )
            )
        }
    }

    /** Немедленная проверка при возврате из банка / Custom Tabs / deep link. */
    fun refreshPaymentStatus(paymentId: Int) {
        viewModelScope.launch {
            checkOnce(paymentId, attempt = 0)
        }
    }

    /** @return true если обработка завершена (успех или ошибка) */
    private suspend fun checkOnce(paymentId: Int, attempt: Int): Boolean {
        return when (val result = subscriptionRepository.getPaymentStatus(paymentId)) {
            is ApiResult.Success -> handleStatus(result.data)
            is ApiResult.Error -> {
                if (attempt >= 2 && (result.code == 401 || result.code == 403)) {
                    _events.emit(
                        PaymentPendingEvent.Failed(
                            "Сессия истекла. Выйдите и войдите снова, затем проверьте абонемент в профиле."
                        )
                    )
                    true
                } else {
                    false
                }
            }
            is ApiResult.Loading -> false
        }
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
