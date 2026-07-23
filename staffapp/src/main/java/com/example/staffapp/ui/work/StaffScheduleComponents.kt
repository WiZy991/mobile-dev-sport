package com.example.staffapp.ui.work

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Room
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffEmptyState
import com.example.staffapp.ui.theme.StaffAccentBlue
import com.example.staffapp.ui.theme.StaffError
import com.example.staffapp.ui.theme.StaffPrimary
import com.example.staffapp.ui.theme.StaffSuccess
import com.example.staffapp.ui.theme.StaffWarning

@Composable
fun StaffScheduleTabContent(
    schedule: ScheduleTabUi,
    onDaySelected: (String) -> Unit,
    onTypeFilterSelected: (String?) -> Unit,
    onSessionClick: (ScheduleSessionUi) -> Unit = {},
) {
    if (schedule.denied) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            StaffEmptyState(schedule.deniedMessage)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (schedule.days.isNotEmpty()) {
            StaffScheduleDateSelector(
                days = schedule.days,
                onDaySelected = onDaySelected,
            )
        }
        StaffScheduleTypeFilters(
            selectedFilter = schedule.selectedTypeFilter,
            onFilterSelected = onTypeFilterSelected,
        )

        when {
            schedule.loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = StaffPrimary)
                }
            }
            schedule.sessions.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.EventBusy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Нет тренировок на выбранную дату.\nНажмите +, чтобы создать запись.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(schedule.sessions, key = { "${it.trainingId}_${it.startTime}_${it.title}" }) { session ->
                        StaffScheduleSessionCard(
                            session = session,
                            onClick = { onSessionClick(session) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffScheduleDateSelector(
    days: List<ScheduleDayUi>,
    onDaySelected: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            StaffScheduleDateItem(
                day = day,
                onClick = { onDaySelected(day.date) },
            )
        }
    }
}

@Composable
private fun StaffScheduleDateItem(
    day: ScheduleDayUi,
    onClick: () -> Unit,
) {
    val backgroundColor = if (day.selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (day.selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f),
        )
        Text(
            text = day.dayNumber,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
        if (day.isToday) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (day.selected) contentColor else MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun StaffScheduleTypeFilters(
    selectedFilter: String?,
    onFilterSelected: (String?) -> Unit,
) {
    val chipScroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(chipScroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("Все") },
        )
        FilterChip(
            selected = selectedFilter == "group",
            onClick = { onFilterSelected("group") },
            label = { Text("Групповые") },
        )
        FilterChip(
            selected = selectedFilter == "personal",
            onClick = { onFilterSelected("personal") },
            modifier = Modifier.widthIn(min = 110.dp),
            label = {
                Text(
                    "Персональные",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        FilterChip(
            selected = selectedFilter == "extra",
            onClick = { onFilterSelected("extra") },
            label = { Text("Допуслуги") },
        )
    }
}

@Composable
fun StaffScheduleSessionCard(
    session: ScheduleSessionUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = session.startTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${session.durationMinutes} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(60.dp)
                    .background(
                        color = typeAccentColor(session.type),
                        shape = RoundedCornerShape(2.dp),
                    ),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = session.trainer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Room,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = session.room,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                sessionSpotsLabel(session)?.let { label ->
                    when {
                        label.isFull -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Мест нет") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = StaffError.copy(alpha = 0.1f),
                                    labelColor = StaffError,
                                ),
                            )
                        }
                        label.spotsLeft != null && label.spotsLeft <= 3 -> {
                            Text(
                                text = "Осталось ${label.spotsLeft}",
                                style = MaterialTheme.typography.labelMedium,
                                color = StaffWarning,
                            )
                        }
                        label.spotsLeft != null -> {
                            Text(
                                text = "Осталось ${label.spotsLeft}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        label.clientCount > 0 -> {
                            AssistChip(
                                onClick = {},
                                label = { Text("Записано ${label.clientCount}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = StaffSuccess.copy(alpha = 0.1f),
                                    labelColor = StaffSuccess,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SessionSpotsLabel(
    val spotsLeft: Int? = null,
    val clientCount: Int = 0,
    val isFull: Boolean = false,
)

private fun sessionSpotsLabel(session: ScheduleSessionUi): SessionSpotsLabel? {
    val booked = session.bookedCount
    val max = session.maxParticipants
    if (booked != null && max != null) {
        val left = (max - booked).coerceAtLeast(0)
        return SessionSpotsLabel(
            spotsLeft = left,
            isFull = booked >= max,
        )
    }
    if (session.clientNames.isNotEmpty()) {
        return SessionSpotsLabel(clientCount = session.clientNames.size)
    }
    return null
}

private fun typeAccentColor(type: String): Color = when (type) {
    "group" -> StaffAccentBlue
    "personal" -> StaffPrimary
    "extra" -> StaffSuccess
    else -> StaffWarning
}
