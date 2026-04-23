package com.fitnessclub.app.ui.screens.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.model.Intensity
import com.fitnessclub.app.data.model.Training
import com.fitnessclub.app.data.model.TrainingType
import com.fitnessclub.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDetailsScreen(
    viewModel: TrainingDetailsViewModel,
    trainingId: String,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(trainingId) {
        viewModel.loadTraining(trainingId)
    }
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrainingDetailsEvent.BookingSuccess -> {
                    snackbarHostState.showSnackbar("Вы успешно записаны на тренировку!")
                }
                is TrainingDetailsEvent.BookingError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is TrainingDetailsEvent.WaitingListSuccess -> {
                    snackbarHostState.showSnackbar("Вы добавлены в лист ожидания")
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тренировка") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null && uiState.training == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadTraining(trainingId) }) {
                            Text("Повторить")
                        }
                    }
                }
                uiState.training != null -> {
                    TrainingDetailsContent(
                        training = uiState.training!!,
                        isBooking = uiState.isBooking,
                        onBookClick = viewModel::bookTraining,
                        onWaitingListClick = viewModel::joinWaitingList
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingDetailsContent(
    training: Training,
    isBooking: Boolean,
    onBookClick: () -> Unit,
    onWaitingListClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header with training info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
                .padding(24.dp)
        ) {
            Column {
                // Training type badge
                Surface(
                    color = if (training.type == TrainingType.GROUP) 
                        AccentBlue.copy(alpha = 0.1f) 
                    else 
                        Primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (training.type == TrainingType.GROUP) 
                            "Групповая" 
                        else 
                            "Персональная",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (training.type == TrainingType.GROUP) AccentBlue else Primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = training.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = training.description.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Details section
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Time info
            DetailRow(
                icon = Icons.Default.Schedule,
                title = "Время",
                value = "${formatHm(training.startTime)} - ${formatHm(training.endTime)}",
                subtitle = "${training.durationMinutes} минут"
            )
            
            // Date info
            DetailRow(
                icon = Icons.Default.CalendarToday,
                title = "Дата",
                value = formatIsoDate(training.startTime)
            )
            
            // Room info
            DetailRow(
                icon = Icons.Default.Room,
                title = "Зал",
                value = training.room
            )
            
            // Intensity
            DetailRow(
                icon = Icons.Default.Speed,
                title = "Интенсивность",
                value = getIntensityText(training.intensity),
                valueColor = getIntensityColor(training.intensity)
            )
            
            // Spots
            DetailRow(
                icon = Icons.Default.Group,
                title = "Места",
                value = "${training.currentParticipants} / ${training.maxParticipants}",
                subtitle = if (training.spotsLeft > 0) 
                    "Осталось ${training.spotsLeft} мест" 
                else 
                    "Мест нет"
            )
            
            HorizontalDivider()
            
            // Trainer section
            TrainerCard(training = training)
            
            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom button
        }
    }
    
    // Bottom booking button
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        when {
            training.isBooked -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Success,
                        disabledContentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Вы записаны",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            training.isFull -> {
                OutlinedButton(
                    onClick = onWaitingListClick,
                    enabled = !isBooking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isBooking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Записаться в лист ожидания",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            else -> {
                Button(
                    onClick = onBookClick,
                    enabled = !isBooking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isBooking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Записаться",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrainerCard(training: Training) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trainer avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = training.trainer.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Тренер",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = training.trainer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                training.trainer.specialization?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Rating
            if (training.trainer.rating > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", training.trainer.rating),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun getIntensityText(intensity: Intensity): String {
    return when (intensity) {
        Intensity.LOW -> "Низкая"
        Intensity.MEDIUM -> "Средняя"
        Intensity.HIGH -> "Высокая"
    }
}

private fun getIntensityColor(intensity: Intensity): Color {
    return when (intensity) {
        Intensity.LOW -> AccentGreen
        Intensity.MEDIUM -> Warning
        Intensity.HIGH -> Error
    }
}

private fun formatHm(value: String): String {
    if (value.isBlank()) return "--:--"
    val t = value.indexOf('T')
    if (t >= 0 && t + 6 <= value.length) return value.substring(t + 1, t + 6)
    if (value.length >= 5) return value.substring(0, 5)
    return "--:--"
}

private fun formatIsoDate(value: String): String {
    if (value.isBlank()) return "—"
    val t = value.indexOf('T')
    if (t > 0) return value.substring(0, t)
    return if (value.length >= 10) value.substring(0, 10) else value
}
