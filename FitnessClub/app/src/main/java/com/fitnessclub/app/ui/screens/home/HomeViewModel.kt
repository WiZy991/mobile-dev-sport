package com.fitnessclub.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.BookingStatus
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val promoTitle: String = "СКИДКА 20%!",
    val promoSubtitle: String = "на все карты 12 и 6 месяцев",
    val unreadNotifications: Int = 3,
    val upcomingTrainings: List<UpcomingTraining> = emptyList(),
    val occupancyCurrent: Int? = null,
    val occupancyMax: Int? = null,
    val occupancyPercentage: Int? = null,
    val occupancyStatus: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    fun loadUnreadCount() {
        viewModelScope.launch {
            try {
                val res = api.getNotifications()
                if (res.isSuccessful) {
                    val count = (res.body() ?: emptyList()).count { !it.isRead }
                    _uiState.update { it.copy(unreadNotifications = count) }
                }
            } catch (_: Exception) { }
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
                                occupancyStatus = occ.status
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }
    
    private fun loadData() {
        viewModelScope.launch {
            loadOccupancy()
            loadUnreadCount()
            try {
                val clubRes = api.getClubInfo()
                if (clubRes.isSuccessful) {
                    clubRes.body()?.let { c ->
                        _uiState.update { s ->
                            s.copy(
                                promoTitle = c.promoTitle?.takeIf { it.isNotBlank() } ?: s.promoTitle,
                                promoSubtitle = c.promoSubtitle?.takeIf { it.isNotBlank() } ?: s.promoSubtitle
                            )
                        }
                    }
                }
            } catch (_: Exception) { }

            try {
                val bookRes = api.getMyBookings()
                if (bookRes.isSuccessful) {
                    val upcoming = (bookRes.body() ?: emptyList())
                        .filter { it.status != BookingStatus.CANCELLED }
                        .sortedBy { it.training.startTime }
                        .take(8)
                        .map { b ->
                            val t = b.training
                            val time = try {
                                t.startTime.substring(11, 16)
                            } catch (_: Exception) {
                                "—"
                            }
                            UpcomingTraining(
                                id = t.id,
                                name = t.name,
                                time = time,
                                trainer = t.trainer.name,
                                room = t.room
                            )
                        }
                    _uiState.update { it.copy(upcomingTrainings = upcoming) }
                }
            } catch (_: Exception) { }
        }
    }
}
