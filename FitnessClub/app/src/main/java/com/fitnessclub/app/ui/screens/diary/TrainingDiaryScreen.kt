package com.fitnessclub.app.ui.screens.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDiaryScreen(
    viewModel: TrainingDiaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дневник тренировок") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Назад") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Default.Add, contentDescription = "Добавить тренировку")
            }
        }
    ) { padding ->
        if (uiState.entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Пока нет записей",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 36.dp, bottom = 8.dp)
                )
                Text(
                    text = "Нажмите + и добавьте первую тренировку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp,
                )
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    DiaryEntryCard(
                        dateLabel = entry.dateMillis.toDateTimeLabel(),
                        title = entry.title,
                        durationMinutes = entry.durationMinutes,
                        notes = entry.notes,
                        onDelete = { viewModel.deleteEntry(entry.id) }
                    )
                }
            }
        }

        if (uiState.isAddDialogOpen) {
            AlertDialog(
                onDismissRequest = viewModel::closeAddDialog,
                title = { Text("Новая запись") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = uiState.draftTitle,
                            onValueChange = viewModel::onTitleChanged,
                            label = { Text("Название тренировки") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = uiState.draftDuration,
                            onValueChange = viewModel::onDurationChanged,
                            label = { Text("Длительность (мин)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = uiState.draftNotes,
                            onValueChange = viewModel::onNotesChanged,
                            label = { Text("Заметка") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            minLines = 2,
                            maxLines = 4,
                        )
                        uiState.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::saveEntry) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeAddDialog) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@Composable
private fun DiaryEntryCard(
    dateLabel: String,
    title: String,
    durationMinutes: Int?,
    notes: String,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить запись")
                }
            }
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            durationMinutes?.let {
                Text(
                    text = "Длительность: $it мин",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (notes.isNotBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private fun Long.toDateTimeLabel(): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
    return formatter.format(Date(this))
}
