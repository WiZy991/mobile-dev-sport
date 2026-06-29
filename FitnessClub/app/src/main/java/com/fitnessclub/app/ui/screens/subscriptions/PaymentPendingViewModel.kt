package com.fitnessclub.app.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun pollPaymentStatus(paymentId: Int) {
        viewModelScope.launch {
            val maxAttempts = 40
            repeat(maxAttempts) { attempt ->
                when (val result = subscriptionRepository.getPaymentStatus(paymentId)) {
                    is ApiResult.Success -> {
                        when (result.data.status) {
                            "paid" -> {
                                delay(500)
                                _events.emit(PaymentPendingEvent.Success)
                                return@launch
                            }
                            "failed", "expired", "cancelled" -> {
                                val reason = result.data.failureReason ?: when (result.data.status) {
                                    "expired" -> "Время оплаты истекло"
                                    "cancelled" -> "Оплата отменена"
                                    else -> "Оплата не прошла"
                                }
                                _events.emit(PaymentPendingEvent.Failed(reason))
                                return@launch
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        if (attempt >= 3 && (result.code == 401 || result.code == 403)) {
                            _events.emit(
                                PaymentPendingEvent.Failed(
                                    "Сессия истекла. Вернитесь в приложение и проверьте абонемент в профиле."
                                )
                            )
                            return@launch
                        }
                    }
                    is ApiResult.Loading -> Unit
                }
                delay(3000)
            }
            _events.emit(
                PaymentPendingEvent.Failed(
                    "Не удалось подтвердить оплату. Если деньги списались — обратитесь в клуб."
                )
            )
        }
    }
}
