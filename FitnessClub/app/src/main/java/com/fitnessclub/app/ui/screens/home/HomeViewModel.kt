package com.fitnessclub.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.ClubPromotion
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.repository.NotificationRepository
import com.fitnessclub.app.data.model.BookingStatus
import com.fitnessclub.app.data.repository.AccessRepository
import com.fitnessclub.app.data.repository.ClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    /** Пока false — баннер не рисуем (нет вспышки демо «СКИДКА 20%»). */
    val promotionsReady: Boolean = false,
    val clubBrandName: String = "Доброзал",
    val promotions: List<ClubPromotion> = emptyList(),
    val unreadNotifications: Int = 0,
    val upcomingTrainings: List<UpcomingTraining> = emptyList(),
    val occupancyCurrent: Int? = null,
    val occupancyMax: Int? = null,
    val occupancyPercentage: Int? = null,
    val occupancyStatus: String? = null,
    val isInsideGym: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: FitnessApi,
    private val notificationRepository: NotificationRepository,
    private val accessRepository: AccessRepository,
    private val clubRepository: ClubRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            coroutineScope {
                val occupancyJob = async { loadOccupancySuspend() }
                val unreadJob = async { loadUnreadCountSuspend() }
                val clubJob = async { loadClubBrandName() }
                val accessJob = async {
                    accessRepository.refreshAccessStatus()
                    _uiState.update { it.copy(isInsideGym = accessRepository.accessStatus.value.isInside) }
                }
                val promotionsJob = async { loadPromotions() }
                val bookingsJob = async { loadUpcomingBookings() }

                // Не ждём все подряд для UI — каждый апдейтит state сам; await только чтобы
                // coroutineScope не завершился раньше дочерних задач (и для pull-to-refresh).
                occupancyJob.await()
                unreadJob.await()
                clubJob.await()
                accessJob.await()
                promotionsJob.await()
                bookingsJob.await()
            }
        }
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
            try {
                val res = api.getUnreadNotificationsCount()
                if (res.isSuccessful) {
                    val count = res.body()?.unreadCount ?: 0
                    _uiState.update { it.copy(unreadNotifications = count) }
                    return@launch
                }
            } catch (_: Exception) {
            }
            // Fallback на полный список, если новый endpoint ещё не на проде.
            notificationRepository.getNotifications().collect { result ->
                when (result) {
                    is ApiResult.Loading -> Unit
                    is ApiResult.Success -> {
                        val count = result.data.count { !it.isRead }
                        _uiState.update { it.copy(unreadNotifications = count) }
                    }
                    is ApiResult.Error -> {
                        _uiState.update { it.copy(unreadNotifications = 0) }
                    }
                }
            }
        }
    }

    fun loadOccupancy() {
        viewModelScope.launch {
            loadOccupancySuspend()
        }
    }

    private suspend fun loadOccupancySuspend() {
        try {
            val res = api.getClubOccupancy()
            if (res.isSuccessful) {
                res.body()?.let { occ ->
                    _uiState.update {
                        it.copy(
                            occupancyCurrent = occ.current,
                            occupancyMax = occ.maxCapacity,
                            occupancyPercentage = occ.percentage,
                            occupancyStatus = occ.status,
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun loadUnreadCountSuspend() {
        try {
            val res = api.getUnreadNotificationsCount()
            if (res.isSuccessful) {
                val count = res.body()?.unreadCount ?: 0
                _uiState.update { it.copy(unreadNotifications = count) }
                return
            }
        } catch (_: Exception) {
        }
        try {
            when (val result = notificationRepository.getNotificationsOnce()) {
                is ApiResult.Success -> {
                    val count = result.data.count { !it.isRead }
                    _uiState.update { it.copy(unreadNotifications = count) }
                }
                else -> _uiState.update { it.copy(unreadNotifications = 0) }
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(unreadNotifications = 0) }
        }
    }

    private suspend fun loadUpcomingBookings() {
        try {
            val bookRes = api.getMyBookings(upcoming = true)
            if (bookRes.isSuccessful) {
                val upcoming = (bookRes.body() ?: emptyList())
                    .filter { it.status != BookingStatus.CANCELLED }
                    .sortedBy { it.training.safeStartTime }
                    .take(8)
                    .map { b ->
                        val t = b.training
                        val time = try {
                            t.safeStartTime.substring(11, 16)
                        } catch (_: Exception) {
                            "—"
                        }
                        UpcomingTraining(
                            id = t.safeId,
                            name = t.safeName,
                            time = time,
                            trainer = t.safeTrainerName,
                            room = t.room,
                        )
                    }
                _uiState.update { it.copy(upcomingTrainings = upcoming) }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun loadPromotions() {
        try {
            val promosRes = api.getClubPromotions()
            if (promosRes.isSuccessful) {
                val promos = (promosRes.body() ?: emptyList())
                    .sortedBy { it.sortOrder }
                    .filterNot { isLegacyDemoPromo(it.title, it.subtitle) }
                _uiState.update {
                    it.copy(promotions = promos, promotionsReady = true)
                }
                return
            }
        } catch (_: Exception) {
        }
        _uiState.update { it.copy(promotions = emptyList(), promotionsReady = true) }
    }

    private fun isLegacyDemoPromo(title: String?, subtitle: String?): Boolean {
        val t = title?.trim()?.uppercase().orEmpty()
        val s = subtitle?.trim()?.lowercase().orEmpty()
        return t.contains("СКИДКА 20%") ||
            (t == "СКИДКА 20%!" && s.contains("12 и 6"))
    }

    private suspend fun loadClubBrandName() {
        try {
            when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(clubBrandName = result.data.name.ifBlank { "Доброзал" })
                    }
                }
                else -> Unit
            }
        } catch (_: Exception) {
        }
    }
}
