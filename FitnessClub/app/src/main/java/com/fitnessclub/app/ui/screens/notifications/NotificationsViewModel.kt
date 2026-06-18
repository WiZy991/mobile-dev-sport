package com.fitnessclub.app.ui.screens.notifications

import com.fitnessclub.app.data.api.ApiNotification
import com.fitnessclub.app.data.api.FitnessApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<NotificationItem> = emptyList()
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    init {
        loadNotifications()
    }
    
    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val res = api.getNotifications()
                if (res.isSuccessful) {
                    val list = res.body() ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notifications = list.map { mapToNotificationItem(it) }
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, notifications = mockFallback()) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, notifications = mockFallback()) }
            }
        }
    }
    
    private fun mapToNotificationItem(n: ApiNotification): NotificationItem {
        val type = when (n.type) {
            "training_reminder" -> NotificationType.TRAINING_REMINDER
            "booking_confirmed" -> NotificationType.BOOKING_CONFIRMED
            "booking_cancelled" -> NotificationType.BOOKING_CANCELLED
            "spot_freed" -> NotificationType.SPOT_FREED
            "schedule_change" -> NotificationType.SCHEDULE_CHANGE
            "promo" -> NotificationType.PROMO
            "subscription" -> NotificationType.SUBSCRIPTION
            "bonus" -> NotificationType.BONUS
            else -> NotificationType.SYSTEM
        }
        return NotificationItem(
            id = n.id,
            type = type,
            title = n.title,
            message = n.message,
            time = formatTimeAgo(n.createdAt),
            isRead = n.isRead
        )
    }
    
    private fun formatTimeAgo(iso: String): String {
        return try {
            val instant = Instant.parse(iso.replace(" ", "T"))
            val created = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            val now = LocalDateTime.now()
            val mins = ChronoUnit.MINUTES.between(created, now)
            val hours = ChronoUnit.HOURS.between(created, now)
            val days = ChronoUnit.DAYS.between(created, now)
            when {
                mins < 60 -> "${mins} мин"
                hours < 24 -> "$hours ч"
                days == 1L -> "Вчера"
                days < 7 -> "$days дней"
                else -> "Неделю"
            }
        } catch (_: Exception) {
            ""
        }
    }
    
    private fun mockFallback(): List<NotificationItem> = listOf(
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
                    message = "Спасибо за регистрацию в Доброзал! Вам начислено 50 приветственных бонусов.",
                    time = "Неделю",
                    isRead = true
                )
            )
    
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                api.markNotificationRead(notificationId)
            } catch (_: Exception) { }
        }
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { n ->
                    if (n.id == notificationId) n.copy(isRead = true) else n
                }
            )
        }
    }
    
    fun markAllAsRead() {
        viewModelScope.launch {
            try {
                api.markAllNotificationsRead()
            } catch (_: Exception) { }
        }
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
    }
}
