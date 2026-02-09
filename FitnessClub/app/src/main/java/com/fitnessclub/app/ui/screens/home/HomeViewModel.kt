package com.fitnessclub.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val promoTitle: String = "СКИДКА 20%!",
    val promoSubtitle: String = "на все карты 12 и 6 месяцев",
    val unreadNotifications: Int = 3,
    val upcomingTrainings: List<UpcomingTraining> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    upcomingTrainings = listOf(
                        UpcomingTraining(
                            id = "1",
                            name = "Йога",
                            time = "09:00",
                            trainer = "Мария И.",
                            room = "Зал йоги"
                        ),
                        UpcomingTraining(
                            id = "2",
                            name = "Силовая",
                            time = "11:00",
                            trainer = "Алексей П.",
                            room = "Тренажёрный зал"
                        ),
                        UpcomingTraining(
                            id = "3",
                            name = "Кардио",
                            time = "14:00",
                            trainer = "Елена С.",
                            room = "Аэробный зал"
                        )
                    )
                )
            }
        }
    }
}
