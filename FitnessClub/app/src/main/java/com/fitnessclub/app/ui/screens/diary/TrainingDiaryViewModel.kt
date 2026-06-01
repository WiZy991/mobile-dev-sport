package com.fitnessclub.app.ui.screens.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessclub.app.data.local.NewTrainingDiaryEntry
import com.fitnessclub.app.data.local.TrainingDiaryEntry
import com.fitnessclub.app.data.local.TrainingDiaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainingDiaryUiState(
    val entries: List<TrainingDiaryEntry> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val draftTitle: String = "",
    val draftDuration: String = "",
    val draftNotes: String = "",
    val error: String? = null,
)

@HiltViewModel
class TrainingDiaryViewModel @Inject constructor(
    private val diaryRepository: TrainingDiaryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrainingDiaryUiState())
    val uiState: StateFlow<TrainingDiaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            diaryRepository.observeEntries().collect { items ->
                _uiState.update { it.copy(entries = items) }
            }
        }
    }

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddDialogOpen = true,
                error = null,
            )
        }
    }

    fun closeAddDialog() {
        _uiState.update {
            it.copy(
                isAddDialogOpen = false,
                draftTitle = "",
                draftDuration = "",
                draftNotes = "",
                error = null,
            )
        }
    }

    fun onTitleChanged(value: String) {
        _uiState.update { it.copy(draftTitle = value, error = null) }
    }

    fun onDurationChanged(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(draftDuration = digitsOnly, error = null) }
    }

    fun onNotesChanged(value: String) {
        _uiState.update { it.copy(draftNotes = value, error = null) }
    }

    fun saveEntry() {
        val state = _uiState.value
        val title = state.draftTitle.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Введите название тренировки") }
            return
        }
        val duration = state.draftDuration.toIntOrNull()
        viewModelScope.launch {
            diaryRepository.addEntry(
                NewTrainingDiaryEntry(
                    dateMillis = System.currentTimeMillis(),
                    title = title,
                    durationMinutes = duration,
                    notes = state.draftNotes,
                )
            )
            closeAddDialog()
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            diaryRepository.deleteEntry(entryId)
        }
    }
}
