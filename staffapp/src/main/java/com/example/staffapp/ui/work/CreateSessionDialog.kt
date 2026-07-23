package com.example.staffapp.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffPrimaryButton

data class CreateSessionDialogUi(
    val date: String,
    val name: String = "Персональная тренировка",
    val type: String = "personal",
    val startTime: String = "10:00",
    val endTime: String = "11:00",
    val room: String = "",
    val maxParticipants: String = "1",
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
fun CreateSessionDialog(
    state: CreateSessionDialogUi,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onMaxParticipantsChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.loading) onDismiss() },
        title = { Text("Новая запись · ${state.date}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == "personal",
                        onClick = { onTypeChange("personal") },
                        label = { Text("Персональная") },
                        enabled = !state.loading,
                    )
                    FilterChip(
                        selected = state.type == "group",
                        onClick = { onTypeChange("group") },
                        label = { Text("Групповая") },
                        enabled = !state.loading,
                    )
                }
                OutlinedTextField(
                    value = state.startTime,
                    onValueChange = onStartTimeChange,
                    label = { Text("Начало (ЧЧ:ММ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = state.endTime,
                    onValueChange = onEndTimeChange,
                    label = { Text("Конец (ЧЧ:ММ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                OutlinedTextField(
                    value = state.room,
                    onValueChange = onRoomChange,
                    label = { Text("Зал (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.loading,
                )
                if (state.type != "personal") {
                    OutlinedTextField(
                        value = state.maxParticipants,
                        onValueChange = onMaxParticipantsChange,
                        label = { Text("Мест") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.loading,
                    )
                }
                state.errorMessage?.let { Text(it) }
                Text("После создания откроется окно, где можно прикрепить клиента.")
            }
        },
        confirmButton = {
            StaffPrimaryButton(
                text = if (state.loading) "Создаём..." else "Создать",
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
