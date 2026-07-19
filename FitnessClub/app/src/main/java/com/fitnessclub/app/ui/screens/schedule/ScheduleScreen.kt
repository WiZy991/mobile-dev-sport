package com.fitnessclub.app.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.model.Intensity
import com.fitnessclub.app.data.model.Training
import com.fitnessclub.app.data.model.TrainingType
import com.fitnessclub.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
    onTrainingClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        TopAppBar(
            title = {
                Text(
                    text = "Расписание",
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        
        // Date selector
        DateSelector(
            selectedDate = uiState.selectedDate,
            onDateSelected = viewModel::selectDate
        )
        
        // Filter chips
        FilterChips(
            selectedFilter = uiState.selectedFilter,
            onFilterSelected = viewModel::selectFilter
        )
        
        // Training list
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.error != null && uiState.trainings.isEmpty()) {
                ErrorState(
                    message = uiState.error!!,
                    onRetry = viewModel::refresh
                )
            } else if (uiState.trainings.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.trainings) { training ->
                        TrainingCard(
                            training = training,
                            onClick = { onTrainingClick(training.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val dates = remember { (0..13).map { today.plusDays(it.toLong()) } }
    val scrollState = rememberScrollState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dates.forEach { date ->
            DateItem(
                date = date,
                isSelected = date == selectedDate,
                isToday = date == today,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
private fun DateItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f)
        )
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
        if (isToday) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isSelected) contentColor else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun FilterChips(
    selectedFilter: TrainingType?,
    onFilterSelected: (TrainingType?) -> Unit
) {
    val chipScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chipScroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("Все") }
        )
        FilterChip(
            selected = selectedFilter == TrainingType.GROUP,
            onClick = { onFilterSelected(TrainingType.GROUP) },
            label = { Text("Групповые") }
        )
        FilterChip(
            selected = selectedFilter == TrainingType.PERSONAL,
            onClick = { onFilterSelected(TrainingType.PERSONAL) },
            modifier = Modifier.widthIn(min = 110.dp),
            label = {
                Text(
                    "Персональные",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
        FilterChip(
            selected = selectedFilter == TrainingType.EXTRA,
            onClick = { onFilterSelected(TrainingType.EXTRA) },
            label = { Text("Допуслуги") }
        )
    }
}

@Composable
fun TrainingCard(
    training: Training,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Time column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatHm(training.startTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${training.durationMinutes} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Divider
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(60.dp)
                    .background(
                        color = getIntensityColor(training.intensity),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Training info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val typeLabel = when (training.type) {
                    TrainingType.EXTRA -> "Допуслуга"
                    TrainingType.GROUP -> "Групповая"
                    TrainingType.PERSONAL -> "Персональная"
                }
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = training.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = training.trainer.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                if (!training.room.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Room,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = training.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Spots left
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (training.isBooked) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Записан") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Success.copy(alpha = 0.1f),
                            labelColor = Success
                        )
                    )
                } else if (training.isFull) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Мест нет") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Error.copy(alpha = 0.1f),
                            labelColor = Error
                        )
                    )
                } else {
                    Text(
                        text = "Осталось ${training.spotsLeft}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (training.spotsLeft <= 3) Warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EventBusy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Нет тренировок на выбранную дату",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getIntensityColor(intensity: Intensity?): Color {
    return when (intensity) {
        Intensity.LOW -> AccentGreen
        Intensity.MEDIUM -> Warning
        Intensity.HIGH -> Error
        null -> Warning
    }
}

private fun formatHm(value: String): String {
    if (value.isBlank()) return "--:--"
    val t = value.indexOf('T')
    if (t >= 0 && t + 6 <= value.length) return value.substring(t + 1, t + 6)
    if (value.length >= 5) return value.substring(0, 5)
    return "--:--"
}
