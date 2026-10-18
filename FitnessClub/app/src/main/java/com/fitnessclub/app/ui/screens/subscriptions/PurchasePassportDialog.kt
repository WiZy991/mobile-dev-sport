package com.fitnessclub.app.ui.screens.subscriptions

import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.fitnessclub.app.ui.components.calmTextFieldColors
import com.fitnessclub.app.ui.screens.auth.formatRussianPhoneMask
import com.fitnessclub.app.ui.screens.auth.normalizeRussianNationalDigits
import com.fitnessclub.app.ui.screens.profile.DateDotsVisualTransformation
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

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

private const val F_NAME = "name"
private const val F_EMAIL = "email"
private const val F_DOB = "dob"
private const val F_SERIES = "series"
private const val F_NUMBER = "number"
private const val F_ISSUED_BY = "issuedBy"
private const val F_ISSUE_DATE = "issueDate"
private const val F_ADDRESS = "address"
private val FIELD_ORDER = listOf(F_NAME, F_EMAIL, F_DOB, F_SERIES, F_NUMBER, F_ISSUED_BY, F_ISSUE_DATE, F_ADDRESS)

private val strictDisplayFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
private val digitsFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy")

private fun dateDigitsOf(display: String): String = display.filter { it.isDigit() }.take(8)

private fun parseDateDigits(digits: String): LocalDate? {
    if (digits.length != 8) return null
    return try {
        LocalDate.parse(DateDotsVisualTransformation.formatDateDots(digits), strictDisplayFmt)
    } catch (_: Exception) {
        null
    }
}

/** Полноэкранный шаг сверки/заполнения профиля перед согласием и оплатой. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var birthDigits by remember(gate) { mutableStateOf(dateDigitsOf(gate.initialDobDisplay)) }
    var series by remember(gate) { mutableStateOf(gate.series.filter { it.isDigit() }.take(4)) }
    var number by remember(gate) { mutableStateOf(gate.number.filter { it.isDigit() }.take(6)) }
    var issuedBy by remember(gate) { mutableStateOf(gate.issuedBy) }
    var issueDigits by remember(gate) { mutableStateOf(dateDigitsOf(gate.issueDateDisplay)) }
    var address by remember(gate) { mutableStateOf(gate.registrationAddress) }
    var unlocked by remember(gate) { mutableStateOf(setOf<String>()) }
    var fieldErrors by remember(gate) { mutableStateOf(mapOf<String, String>()) }
    var issuePickerOpen by remember { mutableStateOf(false) }
    var birthPickerOpen by remember { mutableStateOf(false) }
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val phoneDisplay = formatRussianPhoneMask(normalizeRussianNationalDigits(gate.phone))
    val review = gate.isReview
    val requesters = remember { FIELD_ORDER.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    val dateMask = remember { DateDotsVisualTransformation() }

    fun editable(key: String, filled: Boolean): Boolean {
        if (!review) return true
        return !filled || key in unlocked || key in fieldErrors
    }

    fun clearError(key: String) {
        if (key in fieldErrors) fieldErrors = fieldErrors - key
    }

    fun validate(): Map<String, String> {
        val today = LocalDate.now()
        val birth = parseDateDigits(birthDigits)
        val issue = parseDateDigits(issueDigits)
        val errors = linkedMapOf<String, String>()
        val fio = name.trim()
        when {
            fio.isEmpty() -> errors[F_NAME] = "Укажите ФИО как в паспорте"
            fio.any { it.isDigit() } -> errors[F_NAME] = "В ФИО не должно быть цифр"
        }
        val mail = email.trim()
        when {
            mail.isEmpty() -> errors[F_EMAIL] = "Укажите email — на него придёт чек и договор"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches() ->
                errors[F_EMAIL] = "Email выглядит неверно, пример: name@mail.ru"
        }
        when {
            birthDigits.isEmpty() -> errors[F_DOB] = "Укажите дату рождения"
            birthDigits.length < 8 -> errors[F_DOB] = "Дата целиком: день, месяц и год (дд.мм.гггг)"
            birth == null -> errors[F_DOB] = "Такой даты нет — проверьте день и месяц"
            birth.isAfter(today) -> errors[F_DOB] = "Дата рождения не может быть в будущем"
            birth.isBefore(today.minusYears(110)) -> errors[F_DOB] = "Проверьте год рождения"
        }
        if (series.length != 4) errors[F_SERIES] = "Серия — 4 цифры, сейчас ${series.length}"
        if (number.length != 6) errors[F_NUMBER] = "Номер — 6 цифр, сейчас ${number.length}"
        if (issuedBy.isBlank()) errors[F_ISSUED_BY] = "Укажите, кем выдан паспорт"
        when {
            issueDigits.isEmpty() -> errors[F_ISSUE_DATE] = "Укажите дату выдачи паспорта"
            issueDigits.length < 8 -> errors[F_ISSUE_DATE] = "Дата целиком: день, месяц и год (дд.мм.гггг)"
            issue == null -> errors[F_ISSUE_DATE] = "Такой даты нет — проверьте день и месяц"
            issue.isAfter(today) -> errors[F_ISSUE_DATE] = "Дата выдачи не может быть в будущем"
            birth != null && issue.isBefore(birth.plusYears(14)) ->
                errors[F_ISSUE_DATE] = "Паспорт выдают с 14 лет — проверьте дату"
        }
        if (address.isBlank()) errors[F_ADDRESS] = "Укажите адрес прописки, как в паспорте"
        return errors
    }

    fun submit() {
        val errors = validate()
        fieldErrors = errors
        val first = FIELD_ORDER.firstOrNull { it in errors }
        if (first != null) {
            scope.launch { requesters.getValue(first).bringIntoView() }
            return
        }
        onConfirm(
            PurchasePassportResult(
                name = name.trim(),
                email = email.trim(),
                series = series,
                number = number,
                issuedBy = issuedBy.trim(),
                issueDateIso = parseDateDigits(issueDigits)!!.format(isoFmt),
                registrationAddress = address.trim(),
                dateOfBirthIso = parseDateDigits(birthDigits)?.format(isoFmt),
            ),
        )
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
                        issueDigits = d.format(digitsFmt)
                        clearError(F_ISSUE_DATE)
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
                        birthDigits = d.format(digitsFmt)
                        clearError(F_DOB)
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
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            @Suppress("DEPRECATION")
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
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
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isLoading) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Эти данные попадут в договор с клубом. Проверьте ФИО, дату рождения, паспорт, адрес и email. Телефон менять здесь нельзя.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    ProfileReviewField(
                        value = name,
                        onValueChange = { name = it; clearError(F_NAME) },
                        label = "ФИО",
                        enabled = !isLoading && editable(F_NAME, gate.name.isNotBlank()),
                        onUnlock = { unlocked = unlocked + F_NAME },
                        showPencil = review,
                        error = fieldErrors[F_NAME],
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(F_NAME)),
                    )
                    TextField(
                        value = phoneDisplay,
                        onValueChange = {},
                        label = { Text("Телефон") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        colors = calmTextFieldColors(),
                    )
                    ProfileReviewField(
                        value = email,
                        onValueChange = { email = it.trim(); clearError(F_EMAIL) },
                        label = "Email",
                        enabled = !isLoading && editable(F_EMAIL, gate.email.isNotBlank()),
                        onUnlock = { unlocked = unlocked + F_EMAIL },
                        showPencil = review,
                        keyboardType = KeyboardType.Email,
                        error = fieldErrors[F_EMAIL],
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(F_EMAIL)),
                    )
                    DateField(
                        digits = birthDigits,
                        onDigitsChange = { birthDigits = it; clearError(F_DOB) },
                        label = "Дата рождения",
                        enabled = !isLoading && editable(F_DOB, gate.initialDobDisplay.isNotBlank()),
                        locked = review && gate.initialDobDisplay.isNotBlank() && F_DOB !in unlocked && F_DOB !in fieldErrors,
                        onUnlock = { unlocked = unlocked + F_DOB },
                        onOpenPicker = { birthPickerOpen = true },
                        isLoading = isLoading,
                        error = fieldErrors[F_DOB],
                        visualTransformation = dateMask,
                        modifier = Modifier.bringIntoViewRequester(requesters.getValue(F_DOB)),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        ProfileReviewField(
                            value = series,
                            onValueChange = {
                                series = it.filter { c -> c.isDigit() }.take(4)
                                clearError(F_SERIES)
                            },
                            label = "Серия",
                            enabled = !isLoading && editable(F_SERIES, gate.series.filter { it.isDigit() }.length == 4),
                            onUnlock = { unlocked = unlocked + F_SERIES },
                            showPencil = review,
                            modifier = Modifier.weight(1f).bringIntoViewRequester(requesters.getValue(F_SERIES)),
                            keyboardType = KeyboardType.Number,
                            error = fieldErrors[F_SERIES],
                        )
                        Spacer(Modifier.width(8.dp))
                        ProfileReviewField(
                            value = number,
                            onValueChange = {
                                number = it.filter { c -> c.isDigit() }.take(6)
                                clearError(F_NUMBER)
                            },
                            label = "Номер",
                            enabled = !isLoading && editable(F_NUMBER, gate.number.filter { it.isDigit() }.length == 6),
                            onUnlock = { unlocked = unlocked + F_NUMBER },
                            showPencil = review,
                            modifier = Modifier.weight(1f).bringIntoViewRequester(requesters.getValue(F_NUMBER)),
                            keyboardType = KeyboardType.Number,
                            error = fieldErrors[F_NUMBER],
                        )
                    }
                    ProfileReviewField(
                        value = issuedBy,
                        onValueChange = { issuedBy = it.take(300); clearError(F_ISSUED_BY) },
                        label = "Кем выдан",
                        enabled = !isLoading && editable(F_ISSUED_BY, gate.issuedBy.isNotBlank()),
                        onUnlock = { unlocked = unlocked + F_ISSUED_BY },
                        showPencil = review,
                        minLines = 2,
                        error = fieldErrors[F_ISSUED_BY],
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(F_ISSUED_BY)),
                    )
                    DateField(
                        digits = issueDigits,
                        onDigitsChange = { issueDigits = it; clearError(F_ISSUE_DATE) },
                        label = "Дата выдачи",
                        enabled = !isLoading && editable(F_ISSUE_DATE, gate.issueDateDisplay.isNotBlank()),
                        locked = review && gate.issueDateDisplay.isNotBlank() && F_ISSUE_DATE !in unlocked && F_ISSUE_DATE !in fieldErrors,
                        onUnlock = { unlocked = unlocked + F_ISSUE_DATE },
                        onOpenPicker = { issuePickerOpen = true },
                        isLoading = isLoading,
                        error = fieldErrors[F_ISSUE_DATE],
                        visualTransformation = dateMask,
                        modifier = Modifier.bringIntoViewRequester(requesters.getValue(F_ISSUE_DATE)),
                    )
                    ProfileReviewField(
                        value = address,
                        onValueChange = { address = it; clearError(F_ADDRESS) },
                        label = "Адрес прописки",
                        enabled = !isLoading && editable(F_ADDRESS, gate.registrationAddress.isNotBlank()),
                        onUnlock = { unlocked = unlocked + F_ADDRESS },
                        showPencil = review,
                        minLines = 2,
                        error = fieldErrors[F_ADDRESS],
                        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requesters.getValue(F_ADDRESS)),
                    )
                }
                Spacer(Modifier.height(8.dp))
                val bottomMessage = when {
                    fieldErrors.isNotEmpty() -> "Исправьте поля, отмеченные красным"
                    else -> error
                }
                bottomMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Button(
                    onClick = { submit() },
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DateField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    locked: Boolean,
    onUnlock: () -> Unit,
    onOpenPicker: () -> Unit,
    isLoading: Boolean,
    error: String?,
    visualTransformation: VisualTransformation,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        TextField(
            value = digits,
            onValueChange = { onDigitsChange(it.filter { c -> c.isDigit() }.take(8)) },
            label = { Text(label) },
            placeholder = { Text("дд.мм.гггг") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            supportingText = if (error != null) { { Text(error) } } else null,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = calmTextFieldColors(),
        )
        if (locked) {
            IconButton(onClick = onUnlock, enabled = !isLoading, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить")
            }
        } else {
            IconButton(onClick = onOpenPicker, enabled = !isLoading, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
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
    error: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = minLines == 1,
            minLines = minLines,
            enabled = enabled,
            isError = error != null,
            supportingText = if (error != null) { { Text(error) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = calmTextFieldColors(),
        )
        if (showPencil && !enabled) {
            IconButton(onClick = onUnlock, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Изменить $label")
            }
        }
    }
}
