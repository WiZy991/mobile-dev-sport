package com.fitnessclub.app.ui.screens.personal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

data class TimeSlot(
    val id: String,
    val time: String,
    val trainerName: String,
    val trainingType: String,
    val room: String,
    val isAvailable: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalTrainingScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonalTrainingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showBookingDialog by remember { mutableStateOf<TimeSlot?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Индивидуальная тренировка") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Month selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousWeek() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущая неделя")
                }
                
                Text(
                    text = uiState.currentMonth,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                FilterChip(
                    onClick = { },
                    label = { Text("месяц") },
                    selected = false
                )
                
                IconButton(onClick = { viewModel.nextWeek() }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Следующая неделя")
                }
            }
            
            // Week days
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.weekDays.forEach { day ->
                    DayChip(
                        dayOfWeek = day.dayOfWeek,
                        dayOfMonth = day.dayOfMonth,
                        isSelected = day.isSelected,
                        isWeekend = day.isWeekend,
                        onClick = { viewModel.selectDay(day) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { },
                    label = { Text(uiState.selectedTrainingType ?: "Все тренировки") },
                    selected = uiState.selectedTrainingType != null,
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                )
                FilterChip(
                    onClick = { },
                    label = { Text(uiState.selectedTrainer ?: "Тренер (все)") },
                    selected = uiState.selectedTrainer != null,
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Time slots
            if (uiState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            uiState.error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retryLoad() }) {
                            Text("Повторить")
                        }
                    }
                }
            } else if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.timeSlots.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет доступных слотов\nна выбранную дату",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.timeSlots) { slot ->
                        TimeSlotItem(
                            slot = slot,
                            onClick = { showBookingDialog = slot }
                        )
                    }
                }
            }
        }
    }
    
    // Booking confirmation dialog
    showBookingDialog?.let { slot ->
        AlertDialog(
            onDismissRequest = { showBookingDialog = null },
            title = { Text("Записаться на тренировку?") },
            text = {
                Column {
                    Text(
                        text = slot.trainingType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Время: ${slot.time}")
                    Text("Тренер: ${slot.trainerName}")
                    Text("Зал: ${slot.room}")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bookSlot(slot)
                        showBookingDialog = null
                    }
                ) {
                    Text("Записаться")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookingDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DayChip(
    dayOfWeek: String,
    dayOfMonth: Int,
    isSelected: Boolean,
    isWeekend: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> Primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isWeekend -> AccentOrange
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = dayOfWeek,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f)
        )
        Text(
            text = dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun TimeSlotItem(
    slot: TimeSlot,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = slot.isAvailable, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (slot.isAvailable) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Text(
                    text = slot.time.split("-")[0],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (slot.isAvailable) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = slot.time.split("-").getOrElse(1) { "" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Divider
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(50.dp)
                    .background(
                        if (slot.isAvailable) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slot.trainingType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (slot.isAvailable) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = slot.trainerName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = slot.room,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!slot.isAvailable) {
                Text(
                    text = "Занято",
                    style = MaterialTheme.typography.labelSmall,
                    color = Error
                )
            }
        }
    }
}
