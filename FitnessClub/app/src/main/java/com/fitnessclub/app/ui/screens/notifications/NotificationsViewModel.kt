package com.fitnessclub.app.ui.screens.notifications

import com.fitnessclub.app.data.api.ApiNotification
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.NotificationRepository
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
    val notifications: List<NotificationItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            notificationRepository.getNotifications().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                notifications = result.data.map { mapToNotificationItem(it) },
                                error = null,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                notifications = emptyList(),
                                error = result.message,
                            )
                        }
                    }
                }
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
            "access_alarm" -> NotificationType.ACCESS_ALARM
            else -> NotificationType.SYSTEM
        }
        return NotificationItem(
            id = n.id,
            type = type,
            title = n.title,
            message = n.message,
            time = formatTimeAgo(n.createdAt),
            isRead = n.isRead,
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
                mins < 1 -> "Только что"
                mins < 60 -> "$mins мин"
                hours < 24 -> "$hours ч"
                days == 1L -> "Вчера"
                days < 7 -> "$days дн."
                days < 30 -> "${days / 7} нед."
                else -> "${days / 30} мес."
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { n ->
                    if (n.id == notificationId) n.copy(isRead = true) else n
                },
            )
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
    }
}
