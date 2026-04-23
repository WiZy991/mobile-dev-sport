package com.fitnessclub.app.ui.screens.trainers

import com.fitnessclub.app.data.api.FitnessApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainersUiState(
    val isLoading: Boolean = true,
    val trainers: List<TrainerInfo> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TrainersViewModel @Inject constructor(
    private val fitnessApi: FitnessApi
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TrainersUiState())
    val uiState: StateFlow<TrainersUiState> = _uiState.asStateFlow()
    
    init {
        loadTrainers()
    }
    
    fun loadTrainers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = fitnessApi.getTrainers()
            if (result.isSuccessful) {
                val apiTrainers = result.body() ?: emptyList()
                val trainers = apiTrainers.map { t ->
                    TrainerInfo(
                        id = t.id.orEmpty(),
                        name = t.name,
                        specialization = t.specialization ?: "",
                        rating = t.rating,
                        reviewsCount = 0,
                        experience = "",
                        description = t.description.orEmpty(),
                    )
                }
                _uiState.update {
                    it.copy(isLoading = false, trainers = trainers)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        trainers = emptyList(),
                        error = result.message() ?: "Ошибка загрузки"
                    )
                }
            }
        }
    }
}
