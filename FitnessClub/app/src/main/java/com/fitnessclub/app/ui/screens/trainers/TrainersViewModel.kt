package com.fitnessclub.app.ui.screens.trainers

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
    val trainers: List<TrainerInfo> = emptyList()
)

@HiltViewModel
class TrainersViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(TrainersUiState())
    val uiState: StateFlow<TrainersUiState> = _uiState.asStateFlow()
    
    init {
        loadTrainers()
    }
    
    private fun loadTrainers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Mock data
            val trainers = listOf(
                TrainerInfo(
                    id = "trainer-1",
                    name = "Мария Иванова",
                    specialization = "Йога, Растяжка, Пилатес",
                    rating = 4.9f,
                    reviewsCount = 156,
                    experience = "8 лет",
                    description = "Сертифицированный инструктор йоги. Помогу обрести гармонию тела и души."
                ),
                TrainerInfo(
                    id = "trainer-2",
                    name = "Алексей Петров",
                    specialization = "Силовой тренинг, Кроссфит",
                    rating = 4.8f,
                    reviewsCount = 203,
                    experience = "10 лет",
                    description = "Мастер спорта по тяжёлой атлетике. Помогу достичь любых силовых целей."
                ),
                TrainerInfo(
                    id = "trainer-3",
                    name = "Елена Сидорова",
                    specialization = "Кардио, Аэробика, Танцы",
                    rating = 4.7f,
                    reviewsCount = 128,
                    experience = "6 лет",
                    description = "Зарядим вас энергией! Худеем весело и эффективно."
                ),
                TrainerInfo(
                    id = "trainer-4",
                    name = "Дмитрий Волков",
                    specialization = "Бокс, MMA, Единоборства",
                    rating = 4.9f,
                    reviewsCount = 178,
                    experience = "12 лет",
                    description = "Чемпион России по боксу. Научу защищаться и держать удар."
                ),
                TrainerInfo(
                    id = "trainer-5",
                    name = "Ольга Козлова",
                    specialization = "Пилатес, Реабилитация",
                    rating = 4.8f,
                    reviewsCount = 94,
                    experience = "7 лет",
                    description = "Восстановление после травм, коррекция осанки, укрепление спины."
                )
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    trainers = trainers
                )
            }
        }
    }
}
