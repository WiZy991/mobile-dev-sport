package com.fitnessclub.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.ui.theme.AccentOrange
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.PrimaryVariant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WeekdayLabels = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
private val MonthTitleFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallVisitsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HallVisitsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    val sessionsByDate = remember(uiState.sessions) { uiState.sessions.groupBy { it.date } }
    val initialMonth = remember(uiState.sessions) {
        uiState.sessions.maxOfOrNull { it.date }?.let { YearMonth.from(it) } ?: YearMonth.from(today)
    }
    var month by remember(initialMonth) { mutableStateOf(initialMonth) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Посещения", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.sessions.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = viewModel::load) {
                        Text("Повторить")
                    }
                }
            }
            uiState.sessions.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "После входа по QR здесь появится календарь",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    MonthHeader(
                        month = month,
                        canGoForward = month < YearMonth.from(today),
                        onPrevious = {
                            month = month.minusMonths(1)
                            selectedDate = null
                        },
                        onNext = {
                            month = month.plusMonths(1)
                            selectedDate = null
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WeekdayHeader()
                    VisitMonthGrid(
                        month = month,
                        sessionsByDate = sessionsByDate,
                        selectedDate = selectedDate,
                        onDayClick = { selectedDate = it },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val selected = selectedDate
                    if (selected == null) {
                        Text(
                            text = "Нажмите на оранжевый день, чтобы увидеть время",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        VisitDayDetail(
                            date = selected,
                            sessions = sessionsByDate[selected].orEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц", tint = Primary)
        }
        Text(
            text = monthTitle(month),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Следующий месяц",
                tint = if (canGoForward) Primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WeekdayLabels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisitMonthGrid(
    month: YearMonth,
    sessionsByDate: Map<LocalDate, List<GymSession>>,
    selectedDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val length = month.lengthOfMonth()
    val rows = (lead + length + 6) / 7
    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val dayNumber = row * 7 + column - lead + 1
                    if (dayNumber !in 1..length) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        val visited = sessionsByDate.containsKey(date)
                        VisitDayCell(
                            dayNumber = dayNumber,
                            visited = visited,
                            selected = date == selectedDate,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitDayCell(
    dayNumber: Int,
    visited: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        !visited -> Color.Transparent
                        selected -> PrimaryVariant
                        else -> AccentOrange
                    },
                )
                .then(
                    if (visited) Modifier.clickable(onClick = onClick) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayNumber.toString(),
                color = if (visited) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (visited) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun VisitDayDetail(
    date: LocalDate,
    sessions: List<GymSession>,
) {
    val ordered = remember(sessions) {
        sessions.sortedBy { it.entry ?: LocalTime.MAX }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AccentOrange.copy(alpha = 0.10f))
            .padding(16.dp),
    ) {
        Text(
            text = visitDateLine(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (ordered.isEmpty()) {
            Text(
                text = "В этот день не было визита",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ordered.forEachIndexed { index, session ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sessionRange(session),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                )
                sessionDuration(session)?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun monthTitle(month: YearMonth): String {
    val raw = month.atDay(1).format(MonthTitleFormatter)
    return raw.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale("ru")) else ch.toString()
    }
}

private fun sessionRange(session: GymSession): String {
    val entry = session.entry ?: return "Время этого визита не сохранилось"
    val start = entry.format(TimeFormatter)
    val exit = session.exit ?: return "$start — выход не зафиксирован"
    val end = exit.format(TimeFormatter)
    val exitDate = session.exitDate
    return if (exitDate != null && exitDate != session.date) {
        "$start — $end, ${visitDateLine(exitDate)}"
    } else {
        "$start — $end"
    }
}

private fun sessionDuration(session: GymSession): String? {
    val entry = session.entry ?: return null
    val exit = session.exit ?: return null
    val exitDate = session.exitDate ?: session.date
    val minutes = Duration.between(session.date.atTime(entry), exitDate.atTime(exit)).toMinutes()
    if (minutes <= 0) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "$hours ч $rest мин"
        hours > 0 -> "$hours ч"
        else -> "$rest мин"
    }
}
