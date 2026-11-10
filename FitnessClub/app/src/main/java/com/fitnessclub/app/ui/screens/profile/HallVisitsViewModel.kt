package com.fitnessclub.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.UserStats
import com.fitnessclub.app.data.api.VisitSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import javax.inject.Inject

data class GymSession(
    val date: LocalDate,
    val entry: LocalTime?,
    val exit: LocalTime?,
    val exitDate: LocalDate?,
)

data class HallVisitsUiState(
    val isLoading: Boolean = true,
    val sessions: List<GymSession> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HallVisitsViewModel @Inject constructor(
    private val api: FitnessApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HallVisitsUiState())
    val uiState: StateFlow<HallVisitsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getUserStats()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessions = gymSessions(body),
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Не удалось загрузить посещения") }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Не удалось загрузить посещения") }
            }
        }
    }
}

private fun gymSessions(stats: UserStats): List<GymSession> {
    val fromSessions = stats.recentVisitSessions.orEmpty().mapNotNull { it.toGymSession() }
    if (fromSessions.isNotEmpty()) return fromSessions.sortedByDescending { it.date.atTime(it.entry ?: LocalTime.MIN) }

    val fromVisits = stats.visits.mapNotNull { iso ->
        val entry = parseClubDateTime(iso) ?: return@mapNotNull null
        GymSession(
            date = entry.toLocalDate(),
            entry = entry.toLocalTime().truncated(),
            exit = null,
            exitDate = null,
        )
    }
    if (fromVisits.isNotEmpty()) return fromVisits

    return stats.visitDates.mapNotNull { iso ->
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return@mapNotNull null
        GymSession(date = date, entry = null, exit = null, exitDate = null)
    }
}

private fun VisitSessionDto.toGymSession(): GymSession? {
    val entry = parseClubDateTime(entryAt) ?: return null
    val exit = parseClubDateTime(exitAt)
    return GymSession(
        date = entry.toLocalDate(),
        entry = entry.toLocalTime().truncated(),
        exit = exit?.toLocalTime()?.truncated(),
        exitDate = exit?.toLocalDate(),
    )
}

private fun parseClubDateTime(iso: String?): LocalDateTime? {
    if (iso.isNullOrBlank()) return null
    runCatching { return OffsetDateTime.parse(iso).toLocalDateTime() }
    return runCatching { LocalDateTime.parse(iso) }.getOrNull()
}

private fun LocalTime.truncated(): LocalTime = withSecond(0).withNano(0)
