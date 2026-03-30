package com.fitnessclub.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FeedbackRequest
import com.fitnessclub.app.data.api.FitnessApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val feedbackSuccess: Boolean = false,
    val feedbackError: String? = null,
    val cacheCleared: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: FitnessApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.cacheDir.deleteRecursively()
                context.externalCacheDir?.deleteRecursively()
            }
            _uiState.update { it.copy(cacheCleared = true) }
        }
    }

    fun submitFeedback(rating: Int, comment: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackError = null) }
            try {
                val res = api.submitFeedback(FeedbackRequest(rating = rating, comment = comment, type = "general"))
                if (res.isSuccessful) {
                    _uiState.update { it.copy(feedbackSuccess = true) }
                    onSuccess()
                } else {
                    _uiState.update { it.copy(feedbackError = "Ошибка отправки") }
                    onError("Ошибка отправки")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(feedbackError = e.message) }
                onError(e.message ?: "Ошибка сети")
            }
        }
    }
}
