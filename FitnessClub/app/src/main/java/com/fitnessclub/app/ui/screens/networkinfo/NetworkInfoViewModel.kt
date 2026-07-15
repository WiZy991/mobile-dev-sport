package com.fitnessclub.app.ui.screens.networkinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.repository.ClubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkInfoUiState(
    val isLoading: Boolean = true,
    val clubName: String = "Доброзал",
    val aboutText: String = "",
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val workingHours: String? = null,
    val address: String? = null,
    val socialVk: String? = null,
    val socialTelegram: String? = null,
)

@HiltViewModel
class NetworkInfoViewModel @Inject constructor(
    private val clubRepository: ClubRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkInfoUiState())
    val uiState: StateFlow<NetworkInfoUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = clubRepository.getClubInfo()) {
                is ApiResult.Success -> {
                    val club = result.data
                    val network = club.network
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clubName = club.name.ifBlank { "Доброзал" },
                            aboutText = network?.about.orEmpty(),
                            phone = club.phone,
                            email = club.email,
                            website = network?.website,
                            workingHours = club.workingHours,
                            address = club.address,
                            socialVk = network?.socialVk,
                            socialTelegram = network?.socialTelegram,
                        )
                    }
                }
                else -> _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
