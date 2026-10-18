package com.fitnessclub.app.ui.screens.trainers

import androidx.lifecycle.SavedStateHandle
import com.fitnessclub.app.data.api.userMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.model.Trainer
import com.fitnessclub.app.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainerDetailsUiState(
    val isLoading: Boolean = true,
    val trainer: Trainer? = null,
    val error: String? = null,
)

@HiltViewModel
class TrainerDetailsViewModel @Inject constructor(
    private val fitnessApi: FitnessApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val trainerId: String = savedStateHandle.get<String>(NavArgs.TRAINER_ID) ?: ""

    private val _uiState = MutableStateFlow(TrainerDetailsUiState())
    val uiState: StateFlow<TrainerDetailsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (trainerId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Не указан тренер") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val res = try {
                fitnessApi.getTrainerDetails(trainerId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage("Не удалось загрузить")) }
                return@launch
            }
            if (res.isSuccessful && res.body() != null) {
                _uiState.update { it.copy(isLoading = false, trainer = res.body()) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Не удалось загрузить данные тренера") }
            }
        }
    }
}
