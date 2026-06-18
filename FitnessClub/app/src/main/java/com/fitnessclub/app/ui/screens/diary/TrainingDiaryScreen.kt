package com.fitnessclub.app.ui.screens.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDiaryScreen(
    viewModel: TrainingDiaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.editor != null) {
        DiaryEditorScreen(
            editor = uiState.editor!!,
            onBack = viewModel::closeEditor,
            onSave = viewModel::saveEditor,
            onTitleChanged = viewModel::onEditorTitleChanged,
            onDurationChanged = viewModel::onEditorDurationChanged,
            onNotesChanged = viewModel::onEditorNotesChanged,
            onTypeSelected = viewModel::onEditorTypeSelected,
            onAddExercise = viewModel::addExerciseRow,
            onRemoveExercise = viewModel::removeExerciseRow,
            onExerciseNameChanged = viewModel::onExerciseNameChanged,
            onExerciseSetsChanged = viewModel::onExerciseSetsChanged,
            onExerciseRepsChanged = viewModel::onExerciseRepsChanged,
            onExerciseWeightChanged = viewModel::onExerciseWeightChanged,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Дневник тренировок",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNewWorkout() },
                containerColor = Primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая тренировка")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            DiaryStatsHero(stats = uiState.stats)
            Column(modifier = Modifier.padding(top = 16.dp)) {
                DiaryQuickStartRow(onTemplateSelected = viewModel::openNewWorkout)
            }
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                DiaryTabRow(
                    selected = uiState.selectedTab,
                    onSelected = viewModel::selectTab,
                )
            }
            when (uiState.selectedTab) {
                DiaryTab.JOURNAL -> DiaryJournalContent(
                    groupedEntries = uiState.groupedEntries,
                    onEntryClick = viewModel::openEditWorkout,
                    onDeleteClick = viewModel::requestDelete,
                )
                DiaryTab.STATS -> DiaryStatsContent(stats = uiState.stats)
            }
        }
    }

    if (uiState.deleteCandidateId != null) {
        DiaryDeleteDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}
