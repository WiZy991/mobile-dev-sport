package com.fitnessclub.app.ui.screens.guestpass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.GuestPass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuestPassUiState(
    val isLoading: Boolean = true,
    val passes: List<GuestPass> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class GuestPassViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuestPassUiState())
    val uiState: StateFlow<GuestPassUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val res = api.getGuestPasses()
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(isLoading = false, passes = res.body() ?: emptyList())
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Ошибка загрузки")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Ошибка сети")
                }
            }
        }
    }

    fun createPass(guestName: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                val res = api.createGuestPass(com.fitnessclub.app.data.api.CreateGuestPassRequest(guestName))
                if (res.isSuccessful) {
                    load()
                    onSuccess()
                } else {
                    val err = "Нужен активный абонемент"
                    _uiState.update { it.copy(error = err) }
                    onError(err)
                }
            } catch (e: Exception) {
                val err = e.message ?: "Ошибка"
                _uiState.update { it.copy(error = err) }
                onError(err)
            }
        }
    }
}
