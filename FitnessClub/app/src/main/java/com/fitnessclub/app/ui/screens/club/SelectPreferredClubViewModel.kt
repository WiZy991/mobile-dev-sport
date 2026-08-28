package com.fitnessclub.app.ui.screens.club

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.ClubItem
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.repository.AuthRepository
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.ui.screens.auth.RegistrationVenues
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectPreferredClubUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val clubs: List<ClubItem> = emptyList(),
    val selectedClubId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SelectPreferredClubViewModel @Inject constructor(
    private val clubRepository: ClubRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectPreferredClubUiState())
    val uiState: StateFlow<SelectPreferredClubUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val currentId = authRepository.getCurrentUser().first()?.clubId?.toString()
            val clubs = resolveClubs()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    clubs = clubs,
                    selectedClubId = currentId,
                    error = if (clubs.isEmpty()) "Нет доступных клубов" else null,
                )
            }
        }
    }

    fun selectClub(clubId: String, onDone: (Boolean) -> Unit) {
        val id = clubId.trim().toIntOrNull()
        if (id == null || id <= 0) {
            onDone(false)
            return
        }
        if (_uiState.value.isSaving) {
            return
        }
        if (clubId == _uiState.value.selectedClubId) {
            onDone(true)
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSaving = true, error = null, selectedClubId = clubId)
            }
            when (val result = authRepository.setPreferredClub(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            selectedClubId = result.data.clubId?.toString() ?: clubId,
                        )
                    }
                    onDone(true)
                }
                is ApiResult.Error -> {
                    val previous = authRepository.getCurrentUser().first()?.clubId?.toString()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            selectedClubId = previous,
                            error = result.message ?: "Не удалось сохранить клуб",
                        )
                    }
                    onDone(false)
                }
                else -> {
                    val previous = authRepository.getCurrentUser().first()?.clubId?.toString()
                    _uiState.update {
                        it.copy(isSaving = false, selectedClubId = previous)
                    }
                    onDone(false)
                }
            }
        }
    }

    private suspend fun resolveClubs(): List<ClubItem> {
        if (!Brand.isWhiteLabel) {
            val fromApi = when (val r = clubRepository.getClubs()) {
                is ApiResult.Success -> r.data.filter { it.id.isNotBlank() }
                else -> emptyList()
            }
            if (fromApi.isNotEmpty()) {
                // Порядок как на регистрации, остальное — в конце.
                val order = RegistrationVenues.orderedCards.map { it.clubId }
                val byId = fromApi.associateBy { it.id }
                val ordered = order.mapNotNull { byId[it] }
                val rest = fromApi.filter { it.id !in order }
                return ordered + rest
            }
            return RegistrationVenues.orderedCards.map { RegistrationVenues.toClubItem(it) }
        }
        return when (val r = clubRepository.getClubs()) {
            is ApiResult.Success -> r.data.filter { it.id.isNotBlank() }
            else -> emptyList()
        }
    }
}
