package com.fitnessclub.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<NotificationItem> = emptyList()
)

@HiltViewModel
class NotificationsViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    init {
        loadNotifications()
    }
    
    private fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Mock notifications
            val notifications = listOf(
                NotificationItem(
                    id = "1",
                    type = NotificationType.TRAINING_REMINDER,
                    title = "Напоминание о тренировке",
                    message = "Йога для начинающих начнётся через 1 час в Зале йоги",
                    time = "30 мин",
                    isRead = false
                ),
                NotificationItem(
                    id = "2",
                    type = NotificationType.BOOKING_CONFIRMED,
                    title = "Запись подтверждена",
                    message = "Вы записаны на Силовую тренировку завтра в 11:00",
                    time = "2 ч",
                    isRead = false
                ),
                NotificationItem(
                    id = "3",
                    type = NotificationType.PROMO,
                    title = "Специальное предложение!",
                    message = "Только сегодня скидка 20% на все абонементы! Промокод: FITNESS20",
                    time = "5 ч",
                    isRead = false
                ),
                NotificationItem(
                    id = "4",
                    type = NotificationType.BONUS,
                    title = "Бонусы начислены",
                    message = "Вам начислено 100 бонусных баллов за посещение тренировки",
                    time = "Вчера",
                    isRead = true
                ),
                NotificationItem(
                    id = "5",
                    type = NotificationType.SCHEDULE_CHANGE,
                    title = "Изменение в расписании",
                    message = "Тренировка CrossFit перенесена с 10:00 на 11:00",
                    time = "Вчера",
                    isRead = true
                ),
                NotificationItem(
                    id = "6",
                    type = NotificationType.SUBSCRIPTION,
                    title = "Абонемент заканчивается",
                    message = "До окончания вашего абонемента осталось 7 дней. Продлите со скидкой 10%!",
                    time = "2 дня",
                    isRead = true
                ),
                NotificationItem(
                    id = "7",
                    type = NotificationType.BOOKING_CANCELLED,
                    title = "Тренировка отменена",
                    message = "К сожалению, тренировка Пилатес 15 января отменена. Приносим извинения.",
                    time = "3 дня",
                    isRead = true
                ),
                NotificationItem(
                    id = "8",
                    type = NotificationType.SYSTEM,
                    title = "Добро пожаловать!",
                    message = "Спасибо за регистрацию в FitnessClub! Вам начислено 50 приветственных бонусов.",
                    time = "Неделю",
                    isRead = true
                )
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    notifications = notifications
                )
            }
        }
    }
    
    fun markAsRead(notificationId: String) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { notification ->
                    if (notification.id == notificationId) {
                        notification.copy(isRead = true)
                    } else {
                        notification
                    }
                }
            )
        }
    }
    
    fun markAllAsRead() {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { it.copy(isRead = true) }
            )
        }
    }
}
