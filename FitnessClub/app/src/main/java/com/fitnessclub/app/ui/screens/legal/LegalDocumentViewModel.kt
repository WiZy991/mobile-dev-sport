package com.fitnessclub.app.ui.screens.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.model.LegalDocumentField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LegalDocumentUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val body: String? = null,
    val fields: List<LegalDocumentField> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LegalDocumentViewModel @Inject constructor(
    private val api: FitnessApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LegalDocumentUiState())
    val uiState: StateFlow<LegalDocumentUiState> = _uiState.asStateFlow()

    fun load(document: LegalDocumentType) {
        viewModelScope.launch {
            _uiState.value = LegalDocumentUiState(isLoading = true, title = document.title)
            try {
                val response = api.getLegalDocument(document.apiSlug)
                if (response.isSuccessful) {
                    val data = response.body()
                    if (data != null) {
                        _uiState.value = LegalDocumentUiState(
                            isLoading = false,
                            title = data.title.ifBlank { document.title },
                            body = data.body,
                            fields = data.fields.orEmpty(),
                        )
                    } else {
                        _uiState.value = LegalDocumentUiState(
                            isLoading = false,
                            title = document.title,
                            error = "Не удалось загрузить документ",
                        )
                    }
                } else {
                    _uiState.value = LegalDocumentUiState(
                        isLoading = false,
                        title = document.title,
                        error = "Документ недоступен (${response.code()})",
                    )
                }
            } catch (_: Exception) {
                _uiState.value = LegalDocumentUiState(
                    isLoading = false,
                    title = document.title,
                    error = "Ошибка сети. Проверьте подключение к интернету.",
                )
            }
        }
    }
}
