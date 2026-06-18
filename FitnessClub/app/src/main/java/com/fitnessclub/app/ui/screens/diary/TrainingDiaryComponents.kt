package com.fitnessclub.app.ui.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.local.TrainingDiaryEntry
import com.fitnessclub.app.ui.components.PrimaryButton
import com.fitnessclub.app.ui.components.ModernCard
import com.fitnessclub.app.ui.theme.AccentBlue
import com.fitnessclub.app.ui.theme.AccentGreen
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.Success
import com.fitnessclub.app.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiaryStatsHero(stats: DiaryStatsUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Ваш прогресс",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DiaryMetricChip(value = stats.workoutsThisWeek.toString(), label = "за неделю", icon = Icons.Default.FitnessCenter)
                DiaryMetricChip(value = stats.minutesThisWeek.toString(), label = "минут", icon = Icons.Default.Timer)
                DiaryMetricChip(value = stats.currentStreak.toString(), label = "дней подряд", icon = Icons.Default.LocalFireDepartment)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Всего тренировок: ${stats.totalWorkouts}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun DiaryMetricChip(value: String, label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
fun DiaryQuickStartRow(onTemplateSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Быстрый старт",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickStartChip("Силовая", Icons.Default.FitnessCenter, WorkoutTypes.STRENGTH, onTemplateSelected)
            QuickStartChip("Кардио", Icons.Default.DirectionsRun, WorkoutTypes.CARDIO, onTemplateSelected)
            QuickStartChip("Йога", Icons.Default.SelfImprovement, WorkoutTypes.YOGA, onTemplateSelected)
            QuickStartChip("Персональная", Icons.Default.MenuBook, WorkoutTypes.PERSONAL, onTemplateSelected)
        }
    }
}

@Composable
private fun QuickStartChip(
    label: String,
    icon: ImageVector,
    type: String,
    onClick: (String) -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = { onClick(type) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun DiaryTabRow(selected: DiaryTab, onSelected: (DiaryTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
    ) {
        DiaryTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Primary else Color.Transparent)
                    .clickable { onSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun DiaryJournalContent(
    groupedEntries: List<DiaryDayGroup>,
    onEntryClick: (TrainingDiaryEntry) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    if (groupedEntries.isEmpty()) {
        DiaryEmptyState()
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        groupedEntries.forEach { group ->
            item {
                Text(
                    text = group.dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(group.entries, key = { it.id }) { entry ->
                DiaryEntryCard(
                    entry = entry,
                    onClick = { onEntryClick(entry) },
                    onDelete = { onDeleteClick(entry.id) },
                )
            }
        }
    }
}

@Composable
fun DiaryStatsContent(stats: DiaryStatsUi) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item {
            ModernCard {
                Column {
                    Text("Активность за 7 дней", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        stats.weeklyActivity.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height((day.workoutsCount.coerceAtMost(2) * 24 + 8).dp.coerceAtLeast(8.dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                day.workoutsCount > 0 -> Primary
                                                day.isToday -> Primary.copy(alpha = 0.25f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = day.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (day.isToday) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (stats.typeBreakdown.isNotEmpty()) {
            item {
                ModernCard {
                    Column {
                        Text("Типы тренировок", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        stats.typeBreakdown.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = item.count.toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = Primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        item.count.toFloat() / stats.totalWorkouts.coerceAtLeast(1)
                                    },
                                    modifier = Modifier.width(80.dp),
                                    color = Primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            ModernCard {
                Column {
                    Text("Сводка", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryRow("Тренировок всего", stats.totalWorkouts.toString())
                    SummaryRow("За текущую неделю", stats.workoutsThisWeek.toString())
                    SummaryRow("Минут за неделю", stats.minutesThisWeek.toString())
                    SummaryRow("Серия дней", stats.currentStreak.toString())
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiaryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Дневник пуст",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Запишите тренировку или выберите шаблон быстрого старта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
fun DiaryEntryCard(
    entry: TrainingDiaryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = entry.workoutType ?: WorkoutTypes.CUSTOM
    ModernCard(onClick = onClick) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(typeColor(type).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(typeIcon(type), contentDescription = null, tint = typeColor(type))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${WorkoutTypes.label(type)} · ${entry.dateMillis.toTimeLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
            entry.durationMinutes?.let {
                Text(
                    text = "Длительность: $it мин",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            entry.exercises.orEmpty().take(4).forEach { exercise ->
                Text(
                    text = "• ${exercise.summaryLine()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if ((entry.exercises?.size ?: 0) > 4) {
                Text(
                    text = "+ ещё ${entry.exercises!!.size - 4} упражнений",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = Primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Открыть", style = MaterialTheme.typography.labelMedium, color = Primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorScreen(
    editor: DiaryEditorUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (String) -> Unit,
    onExerciseNameChanged: (String, String) -> Unit,
    onExerciseSetsChanged: (String, String) -> Unit,
    onExerciseRepsChanged: (String, String) -> Unit,
    onExerciseWeightChanged: (String, String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editor.isEditing) "Редактирование" else "Новая тренировка",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = onTitleChanged,
                    label = { Text("Название тренировки") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            item {
                Text("Тип", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WorkoutTypes.all.forEach { type ->
                        FilterChip(
                            selected = editor.workoutType == type,
                            onClick = { onTypeSelected(type) },
                            label = { Text(WorkoutTypes.label(type)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = editor.duration,
                    onValueChange = onDurationChanged,
                    label = { Text("Длительность (мин)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Упражнения", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onAddExercise) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Добавить")
                    }
                }
            }
            items(editor.exercises, key = { it.id }) { row ->
                ModernCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = row.name,
                                onValueChange = { onExerciseNameChanged(row.id, it) },
                                label = { Text("Упражнение") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            if (editor.exercises.size > 1) {
                                IconButton(onClick = { onRemoveExercise(row.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить упражнение")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = row.sets,
                                onValueChange = { onExerciseSetsChanged(row.id, it) },
                                label = { Text("Подходы") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = row.reps,
                                onValueChange = { onExerciseRepsChanged(row.id, it) },
                                label = { Text("Повторы") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                            OutlinedTextField(
                                value = row.weight,
                                onValueChange = { onExerciseWeightChanged(row.id, it) },
                                label = { Text("Кг") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = editor.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                )
            }
            editor.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                PrimaryButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (editor.isEditing) "Сохранить изменения" else "Сохранить тренировку")
                }
            }
        }
    }
}

@Composable
fun DiaryDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить запись?") },
        text = { Text("Тренировка будет удалена из дневника без возможности восстановления.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun typeColor(type: String): Color = when (type) {
    WorkoutTypes.STRENGTH -> Primary
    WorkoutTypes.CARDIO -> AccentBlue
    WorkoutTypes.YOGA -> AccentGreen
    WorkoutTypes.PERSONAL -> Warning
    else -> Success
}

private fun typeIcon(type: String): ImageVector = when (type) {
    WorkoutTypes.STRENGTH -> Icons.Default.FitnessCenter
    WorkoutTypes.CARDIO -> Icons.Default.DirectionsRun
    WorkoutTypes.YOGA -> Icons.Default.SelfImprovement
    WorkoutTypes.PERSONAL -> Icons.Default.MenuBook
    else -> Icons.Default.CalendarMonth
}

private fun Long.toTimeLabel(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale("ru", "RU"))
    return formatter.format(Date(this))
}
