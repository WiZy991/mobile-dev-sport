package com.example.staffapp.ui.work

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffPrimaryButton
import java.time.LocalDate
import java.time.LocalTime

data class CreateSessionDialogUi(
    val date: String,
    val name: String = "Персональная тренировка",
    val type: String = "personal",
    val startTime: String = "10:00",
    val durationMinutes: Int = 60,
    val room: String = "",
    val maxParticipants: String = "1",
    /** id занятия при редактировании; null — создание нового. */
    val editingTrainingId: String? = null,
    val loading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = editingTrainingId != null

    val endTime: String
        get() {
            val start = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return ""
            return start.plusMinutes(durationMinutes.toLong()).toString()
        }
}

private val DURATION_OPTIONS = listOf(30, 45, 60, 90)

private val WEEKDAY_NAMES = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

private fun formatDateLabel(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val months = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    val weekday = WEEKDAY_NAMES[date.dayOfWeek.value - 1]
    return "${date.dayOfMonth} ${months[date.monthValue - 1]}, $weekday"
}

@Composable
fun CreateSessionDialog(
    state: CreateSessionDialogUi,
    onNameChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit,
    onRoomChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    fun openDatePicker() {
        val current = runCatching { LocalDate.parse(state.date) }.getOrNull() ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDateChange("%04d-%02d-%02d".format(year, month + 1, day))
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    fun openTimePicker() {
        val current = runCatching { LocalTime.parse(state.startTime) }.getOrNull()
            ?: LocalTime.of(10, 0)
        TimePickerDialog(
            context,
            { _, hour, minute -> onStartTimeChange("%02d:%02d".format(hour, minute)) },
            current.hour,
            current.minute,
            true,
        ).show()
    }

    AlertDialog(
        onDismissRequest = { if (!state.loading) onDismiss() },
        title = { Text(if (state.isEditing) "Изменить запись" else "Новая запись") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                PickerField(
                    value = formatDateLabel(state.date),
                    label = "Дата",
                    icon = Icons.Filled.CalendarMonth,
                    enabled = !state.loading,
                    onClick = ::openDatePicker,
                )
                PickerField(
                    value = state.startTime,
                    label = "Время начала",
                    icon = Icons.Filled.Schedule,
                    enabled = !state.loading,
                    onClick = ::openTimePicker,
                )
                Text(
                    "Длительность",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DURATION_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = state.durationMinutes == minutes,
                            onClick = { onDurationChange(minutes) },
                            enabled = !state.loading,
                            label = { Text("$minutes мин") },
                        )
                    }
                }
                if (state.endTime.isNotBlank()) {
                    Text(
                        "Окончание в ${state.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = state.room,
                    onValueChange = onRoomChange,
                    label = { Text("Зал (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (!state.isEditing) {
                    Text(
                        "После создания откроется окно, где можно прикрепить клиента.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            StaffPrimaryButton(
                text = when {
                    state.loading && state.isEditing -> "Сохраняем..."
                    state.loading -> "Создаём..."
                    state.isEditing -> "Сохранить"
                    else -> "Создать"
                },
                onClick = onCreate,
                enabled = !state.loading,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.loading) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { Icon(icon, contentDescription = label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true,
            enabled = enabled,
        )
        // Прозрачная накладка: readOnly-поле само не пропускает клики.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = enabled, onClick = onClick),
        )
    }
}
