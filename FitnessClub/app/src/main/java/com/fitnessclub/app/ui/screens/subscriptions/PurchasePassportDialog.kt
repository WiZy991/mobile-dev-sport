package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessclub.app.ui.screens.auth.formatRussianPhoneMask
import com.fitnessclub.app.ui.screens.auth.normalizeRussianNationalDigits
import com.fitnessclub.app.ui.theme.Primary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PurchasePassportResult(
    val name: String,
    val email: String,
    val series: String,
    val number: String,
    val issuedBy: String,
    val issueDateIso: String,
    val registrationAddress: String,
    val dateOfBirthIso: String?,
)

/** Полноэкранный шаг сверки/заполнения профиля перед согласием и оплатой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasePassportDialog(
    gate: PurchasePassportGate,
    isLoading: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (PurchasePassportResult) -> Unit,
    onResendEmail: () -> Unit = {},
    isResendingEmail: Boolean = false,
    emailResendMessage: String? = null,
    onConsumeEmailResendMessage: () -> Unit = {},
) {
    var name by remember(gate) { mutableStateOf(gate.name) }
    var email by remember(gate) { mutableStateOf(gate.email) }
    var birthDisplay by remember(gate) { mutableStateOf(gate.initialDobDisplay) }
    var series by remember(gate) { mutableStateOf(gate.series.filter { it.isDigit() }.take(4)) }
    var number by remember(gate) { mutableStateOf(gate.number.filter { it.isDigit() }.take(6)) }
    var issuedBy by remember(gate) { mutableStateOf(gate.issuedBy) }
    var issueDateDisplay by remember(gate) { mutableStateOf(gate.issueDateDisplay) }
    var address by remember(gate) { mutableStateOf(gate.registrationAddress) }
    var unlocked by remember(gate) { mutableStateOf(setOf<String>()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var issuePickerOpen by remember { mutableStateOf(false) }
    var birthPickerOpen by remember { mutableStateOf(false) }
    val displayFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val phoneDisplay = formatRussianPhoneMask(normalizeRussianNationalDigits(gate.phone))
    val review = gate.isReview

    fun editable(key: String, filled: Boolean): Boolean {
        if (!review) return true
        return !filled || key in unlocked
    }

    fun parseDisplayToIso(text: String): String? = try {
        LocalDate.parse(text.trim(), displayFmt).format(isoFmt)
    } catch (_: Exception) {
        try {
            LocalDate.parse(text.trim(), isoFmt).format(isoFmt)
        } catch (_: Exception) {
            null
        }
    }

    if (emailResendMessage != null) {
        AlertDialog(
            onDismissRequest = onConsumeEmailResendMessage,
            title = { Text("Письмо отправлено") },
            text = { Text(emailResendMessage) },
            confirmButton = {
                TextButton(onClick = onConsumeEmailResendMessage) { Text("Хорошо") }
            },
        )
    }

    if (issuePickerOpen) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { issuePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        issueDateDisplay = d.format(displayFmt)
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
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (review) "Проверьте данные профиля" else "Данные профиля",
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Эти данные попадут в договор с клубом. Проверьте ФИО, дату рождения, паспорт, адрес и email. Телефон менять здесь нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileReviewField(
                        value = name,
                        onValueChange = { name = it; localError = null },
                        label = "ФИО",
                        enabled = !isLoading && editable("name", gate.name.isNotBlank()),
                        onUnlock = { unlocked = unlocked + "name" },
                        showPencil = review,
                    )
                    TextField(
                        value = phoneDisplay,
                        onValueChange = {},
                        label = { Text("Телефон") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                    )
                    ProfileReviewField(
                        value = email,
                        onValueChange = { email = it; localError = null },
                        label = "Email",
                        enabled = !isLoading && editable("email", gate.email.isNotBlank()),
                        onUnlock = { unlocked = unlocked + "email" },
                        showPencil = review,
                        keyboardType = KeyboardType.Email,
                    )
                    if (!gate.emailVerified) {
                        Text(
                            "Email ещё не подтверждён. Нажмите «Подтвердить» — покупку можно продолжить после перехода по ссылке из письма.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onResendEmail,
                            enabled = !isResendingEmail && !isLoading,
                        ) {
                            Text(if (isResendingEmail) "Отправляем…" else "Подтвердить")
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = birthDisplay,
                            onValueChange = { birthDisplay = it; localError = null },
                            label = { Text("Дата рождения") },
                            placeholder = { Text("дд.мм.гггг") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isLoading && editable("dob", gate.initialDobDisplay.isNotBlank()),
                            shape = RoundedCornerShape(12.dp),
                        )
                        if (review && gate.initialDobDisplay.isNotBlank() && "dob" !in unlocked) {
                            IconButton(onClick = { unlocked = unlocked + "dob" }, enabled = !isLoading) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить")
                            }
                        } else {
                            IconButton(onClick = { birthPickerOpen = true }, enabled = !isLoading) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ProfileReviewField(
                            value = series,
                            onValueChange = {
                                series = it.filter { c -> c.isDigit() }.take(4)
                                localError = null
                            },
                            label = "Серия",
                            enabled = !isLoading && editable("series", gate.series.filter { it.isDigit() }.length == 4),
                            onUnlock = { unlocked = unlocked + "series" },
                            showPencil = review,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                        Spacer(Modifier.width(8.dp))
                        ProfileReviewField(
                            value = number,
                            onValueChange = {
                                number = it.filter { c -> c.isDigit() }.take(6)
                                localError = null
                            },
                            label = "Номер",
                            enabled = !isLoading && editable("number", gate.number.filter { it.isDigit() }.length == 6),
                            onUnlock = { unlocked = unlocked + "number" },
                            showPencil = review,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    ProfileReviewField(
                        value = issuedBy,
                        onValueChange = { issuedBy = it.take(300); localError = null },
                        label = "Кем выдан",
                        enabled = !isLoading && editable("issuedBy", gate.issuedBy.isNotBlank()),
                        onUnlock = { unlocked = unlocked + "issuedBy" },
                        showPencil = review,
                        minLines = 2,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = issueDateDisplay,
                            onValueChange = { issueDateDisplay = it; localError = null },
                            label = { Text("Дата выдачи") },
                            placeholder = { Text("дд.мм.гггг") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isLoading && editable("issueDate", gate.issueDateDisplay.isNotBlank()),
                            shape = RoundedCornerShape(12.dp),
                        )
                        if (review && gate.issueDateDisplay.isNotBlank() && "issueDate" !in unlocked) {
                            IconButton(onClick = { unlocked = unlocked + "issueDate" }, enabled = !isLoading) {
                                Icon(Icons.Default.Edit, contentDescription = "Изменить")
                            }
                        } else {
                            IconButton(onClick = { issuePickerOpen = true }, enabled = !isLoading) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
                            }
                        }
                    }
                    ProfileReviewField(
                        value = address,
                        onValueChange = { address = it; localError = null },
                        label = "Адрес прописки",
                        enabled = !isLoading && editable("address", gate.registrationAddress.isNotBlank()),
                        onUnlock = { unlocked = unlocked + "address" },
                        showPencil = review,
                        minLines = 2,
                    )
                    (localError ?: error)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val issueIso = parseDisplayToIso(issueDateDisplay)
                        val birthIso = parseDisplayToIso(birthDisplay)
                        when {
                            name.trim().isEmpty() -> localError = "Укажите ФИО как в паспорте"
                            email.trim().isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                                localError = "Укажите корректный email"
                            birthIso == null -> localError = "Укажите дату рождения"
                            series.length != 4 || number.length != 6 || issuedBy.isBlank() || issueIso == null ->
                                localError = "Заполните все поля паспорта корректно"
                            address.isBlank() -> localError = "Укажите адрес прописки"
                            else -> {
                                localError = null
                                onConfirm(
                                    PurchasePassportResult(
                                        name = name.trim(),
                                        email = email.trim(),
                                        series = series,
                                        number = number,
                                        issuedBy = issuedBy.trim(),
                                        issueDateIso = issueIso,
                                        registrationAddress = address.trim(),
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
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (isLoading) "Сохраняем…" else "Продолжить",
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

@Composable
private fun ProfileReviewField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    onUnlock: () -> Unit,
    showPencil: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = minLines == 1,
            minLines = minLines,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
        )
        if (showPencil && !enabled) {
            IconButton(onClick = onUnlock) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить $label")
            }
        }
    }
}
