package com.fitnessclub.app.ui.screens.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.api.PurchaseItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseHistoryUiState(
    val isLoading: Boolean = true,
    val purchases: List<PurchaseItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PurchaseHistoryViewModel @Inject constructor(
    private val api: FitnessApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseHistoryUiState())
    val uiState: StateFlow<PurchaseHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val res = api.getPurchases()
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(isLoading = false, purchases = res.body() ?: emptyList())
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
}
