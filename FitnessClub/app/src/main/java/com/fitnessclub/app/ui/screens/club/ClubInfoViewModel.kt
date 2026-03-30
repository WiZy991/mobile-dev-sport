package com.fitnessclub.app.ui.screens.club

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.ClubInfo
import com.fitnessclub.app.data.repository.ClubRepository
import com.fitnessclub.app.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubInfoUiState(
    val isLoading: Boolean = true,
    val club: ClubInfo? = null,
    val error: String? = null
)

@HiltViewModel
class ClubInfoViewModel @Inject constructor(
    private val clubRepository: ClubRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val clubId: String? = savedStateHandle.get<String>(NavArgs.CLUB_ID)
    private val _uiState = MutableStateFlow(ClubInfoUiState())
    val uiState: StateFlow<ClubInfoUiState> = _uiState.asStateFlow()

    init {
        loadClubInfo()
    }

    fun loadClubInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = if (clubId != null) {
                clubRepository.getClubDetails(clubId)
            } else {
                clubRepository.getClubInfo()
            }
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, club = result.data)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                is ApiResult.Loading -> { /* no-op */ }
            }
        }
    }
}
