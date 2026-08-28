package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessclub.app.ui.screens.auth.PassportDraft
import com.fitnessclub.app.ui.theme.Primary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PurchasePassportResult(
    val series: String,
    val number: String,
    val issuedBy: String,
    val issueDateIso: String,
    val registrationAddress: String,
    val dateOfBirthIso: String?,
)

/** Обязательный шаг перед Альфой: паспорт, если ещё не заполнен в профиле. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasePassportDialog(
    initialDateOfBirthDisplay: String = "",
    needDateOfBirth: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (PurchasePassportResult) -> Unit,
) {
    var draft by remember { mutableStateOf(PassportDraft()) }
    var birthDisplay by remember { mutableStateOf(initialDateOfBirthDisplay) }
    var localError by remember { mutableStateOf<String?>(null) }
    var issuePickerOpen by remember { mutableStateOf(false) }
    var birthPickerOpen by remember { mutableStateOf(false) }
    val displayFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    fun parseDisplayToIso(text: String): String? = try {
        LocalDate.parse(text.trim(), displayFmt).format(isoFmt)
    } catch (_: Exception) {
        try {
            LocalDate.parse(text.trim(), isoFmt).format(isoFmt)
        } catch (_: Exception) {
            null
        }
    }

    if (issuePickerOpen) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { issuePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        draft = draft.copy(issuedDateDisplay = d.format(displayFmt))
                    }
                    issuePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { issuePickerOpen = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (birthPickerOpen) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { birthPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        birthDisplay = d.format(displayFmt)
                    }
                    birthPickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { birthPickerOpen = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "Паспортные данные",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Для покупки абонемента нужны паспортные данные. Заполните один раз — дальше этот шаг не появится.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (needDateOfBirth) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = birthDisplay,
                                onValueChange = { birthDisplay = it; localError = null },
                                label = { Text("Дата рождения") },
                                placeholder = { Text("дд.мм.гггг") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !isLoading,
                            )
                            IconButton(onClick = { birthPickerOpen = true }, enabled = !isLoading) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = draft.series,
                            onValueChange = {
                                draft = draft.copy(series = it.filter { c -> c.isDigit() }.take(4))
                                localError = null
                            },
                            label = { Text("Серия") },
                            placeholder = { Text("0000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isLoading,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = draft.number,
                            onValueChange = {
                                draft = draft.copy(number = it.filter { c -> c.isDigit() }.take(6))
                                localError = null
                            },
                            label = { Text("Номер") },
                            placeholder = { Text("000000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !isLoading,
                        )
                    }
                    TextField(
                        value = draft.issuedBy,
                        onValueChange = {
                            draft = draft.copy(issuedBy = it.take(300))
                            localError = null
                        },
                        label = { Text("Кем выдан") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !isLoading,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = draft.issuedDateDisplay,
                            onValueChange = {
                                draft = draft.copy(issuedDateDisplay = it)
                                localError = null
                            },
                            label = { Text("Дата выдачи") },
                            placeholder = { Text("дд.мм.гггг") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isLoading,
                        )
                        IconButton(onClick = { issuePickerOpen = true }, enabled = !isLoading) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
                        }
                    }
                    Text(
                        "Адрес прописки",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextField(
                        value = draft.region,
                        onValueChange = { draft = draft.copy(region = it); localError = null },
                        label = { Text("Регион/область") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                    )
                    TextField(
                        value = draft.city,
                        onValueChange = { draft = draft.copy(city = it); localError = null },
                        label = { Text("Город") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                    )
                    TextField(
                        value = draft.streetHouse,
                        onValueChange = { draft = draft.copy(streetHouse = it); localError = null },
                        label = { Text("Улица, дом, квартира") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading,
                    )
                    (localError ?: error)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val issueIso = parseDisplayToIso(draft.issuedDateDisplay)
                        val birthIso = if (needDateOfBirth) parseDisplayToIso(birthDisplay) else null
                        when {
                            !draft.isCompleteForRegister() || issueIso == null -> {
                                localError = "Заполните все поля паспорта корректно"
                            }
                            needDateOfBirth && birthIso == null -> {
                                localError = "Укажите дату рождения"
                            }
                            else -> {
                                localError = null
                                val address = listOf(draft.region, draft.city, draft.streetHouse)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                onConfirm(
                                    PurchasePassportResult(
                                        series = draft.series,
                                        number = draft.number,
                                        issuedBy = draft.issuedBy.trim(),
                                        issueDateIso = issueIso,
                                        registrationAddress = address,
                                        dateOfBirthIso = birthIso,
                                    ),
                                )
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(
                        if (isLoading) "Сохраняем…" else "Сохранить и продолжить",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}
