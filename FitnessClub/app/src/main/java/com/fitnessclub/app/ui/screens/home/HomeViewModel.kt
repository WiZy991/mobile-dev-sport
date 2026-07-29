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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val clubBrandName: String = "Доброзал",
    val promotions: List<ClubPromotion> = listOf(
        ClubPromotion(
            id = "default",
            title = "СКИДКА 20%!",
            subtitle = "на все карты 12 и 6 месяцев",
            buttonText = "Подробнее",
            actionType = "shop",
            bgFrom = "#F97316",
            bgTo = "#3B82F6",
            sortOrder = 100
        )
    ),
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
        loadData()
    }

    fun loadUnreadCount() {
        viewModelScope.launch {
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
    }

    private fun loadData() {
        viewModelScope.launch {
            loadOccupancy()
            loadUnreadCount()
            loadClubBrandName()
            accessRepository.refreshAccessStatus()
            _uiState.update { it.copy(isInsideGym = accessRepository.accessStatus.value.isInside) }
            try {
                val promosRes = api.getClubPromotions()
                if (promosRes.isSuccessful) {
                    val promos = (promosRes.body() ?: emptyList()).sortedBy { it.sortOrder }
                    if (promos.isNotEmpty()) {
                        _uiState.update { it.copy(promotions = promos) }
                    } else {
                        loadPromoFallbackFromClubInfo()
                    }
                } else {
                    loadPromoFallbackFromClubInfo()
                }
            } catch (_: Exception) {
                loadPromoFallbackFromClubInfo()
            }

            try {
                val bookRes = api.getMyBookings()
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

    private suspend fun loadPromoFallbackFromClubInfo() {
        try {
            val clubRes = api.getClubInfo()
            if (clubRes.isSuccessful) {
                clubRes.body()?.let { c ->
                    _uiState.update {
                        it.copy(clubBrandName = c.name.ifBlank { "Доброзал" })
                    }
                    val title = c.promoTitle?.takeIf { it.isNotBlank() } ?: return
                    val subtitle = c.promoSubtitle?.takeIf { it.isNotBlank() }
                    _uiState.update {
                        it.copy(
                            promotions = listOf(
                                ClubPromotion(
                                    id = "club-info",
                                    title = title,
                                    subtitle = subtitle,
                                    buttonText = "Подробнее",
                                    actionType = "shop",
                                    bgFrom = "#F97316",
                                    bgTo = "#3B82F6",
                                    sortOrder = 100,
                                )
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
