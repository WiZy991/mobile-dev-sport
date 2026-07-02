package com.fitnessclub.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.collectAsState
import com.fitnessclub.app.R
import com.fitnessclub.app.data.api.ClubItem
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.PrimaryVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToPassport: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onChangeClub: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var birthPickerOpen by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Открытый DropdownMenu держит popup-слой: первый тап снаружи часто только закрывает меню.
    // При прокрутке формы закрываем выпадашки, чтобы кнопка «Зарегистрироваться» стабильно получала нажатия.
    LaunchedEffect(scrollState.value) {
        typeMenuExpanded = false
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RegisterEvent.Success -> onRegisterSuccess()
            }
        }
    }

    if (birthPickerOpen) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { birthPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { viewModel.setBirthDateFromMillis(it) }
                    birthPickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { birthPickerOpen = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Primary, PrimaryVariant)))
    ) {
        val baseScheme = MaterialTheme.colorScheme
        val registerColorScheme = remember(baseScheme) {
            registerFormColorScheme(baseScheme)
        }
        MaterialTheme(colorScheme = registerColorScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onNavigateToLogin) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
            }
            Text(
                "РЕГИСТРАЦИЯ ПОЛЬЗОВАТЕЛЯ",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                textAlign = TextAlign.Center
            )

            SelectedClubSummary(
                club = uiState.selectedClub,
                clubsLoadError = uiState.clubsLoadError,
                clubError = uiState.fieldErr(uiState.clubError),
                onChangeClub = onChangeClub,
            )

            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = uiState.registrationType.label,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = orangeRegisterInputTextStyle(),
                    label = { Text("Тип регистрации", color = Color.White.copy(0.78f)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = orangeFieldColors()
                )
                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                    modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
                ) {
                    RegistrationTypeOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.label) },
                            onClick = {
                                viewModel.onRegistrationTypeChange(opt)
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            OrangeOutlineField(
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange,
                label = "Фамилия",
                error = uiState.fieldErr(uiState.lastNameError),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
            OrangeOutlineField(
                value = uiState.firstName,
                onValueChange = viewModel::onFirstNameChange,
                label = "Имя",
                error = uiState.fieldErr(uiState.firstNameError),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )
            OrangeOutlineField(
                value = uiState.middleName,
                onValueChange = viewModel::onMiddleNameChange,
                label = "Отчество",
                error = null,
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = uiState.birthDateDisplay,
                    onValueChange = viewModel::onBirthDateChange,
                    textStyle = orangeRegisterInputTextStyle(),
                    label = { Text("Дата рождения", color = Color.White.copy(0.78f)) },
                    placeholder = { Text("дд.мм.гггг", color = Color.White.copy(0.45f)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    singleLine = true,
                    colors = orangeFieldColors(),
                    isError = uiState.fieldErr(uiState.birthDateError) != null,
                    supportingText = uiState.fieldErr(uiState.birthDateError)
                        ?.let { { Text(it, color = Color(0xFFFFE0B2)) } }
                )
                IconButton(onClick = { birthPickerOpen = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь", tint = Color.White)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = uiState.phoneNationalDigits,
                    onValueChange = viewModel::onPhoneChange,
                    textStyle = orangeRegisterInputTextStyle(),
                    label = { Text("Номер телефона", color = Color.White.copy(0.78f)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    visualTransformation = remember { RussianPhoneVisualTransformation() },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    singleLine = true,
                    colors = orangeFieldColors(),
                    isError = uiState.fieldErr(uiState.phoneError) != null,
                    supportingText = uiState.fieldErr(uiState.phoneError)
                        ?.let { { Text(it, color = Color(0xFFFFE0B2)) } },
                    placeholder = { Text("+7 (999) 123-45-67", color = Color.White.copy(0.45f)) }
                )
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = Color.White.copy(0.7f))
            }

            OrangeOutlineField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = "E-mail",
                error = uiState.fieldErr(uiState.emailError),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Text("Пол", color = Color.White.copy(0.85f), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GenderOption.entries.forEach { g ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.onGenderChange(g) }
                    ) {
                        RadioButton(
                            selected = uiState.gender == g,
                            onClick = { viewModel.onGenderChange(g) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color.White,
                                unselectedColor = Color.White.copy(0.6f)
                            )
                        )
                        Text(g.label, color = Color.White)
                    }
                }
            }
            uiState.fieldErr(uiState.genderError)?.let {
                Text(it, color = Color.White.copy(0.95f), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            PassportCard(
                summary = uiState.passportSummary,
                error = uiState.fieldErr(uiState.passportError),
                onClick = {
                    viewModel.clearPassportError()
                    onNavigateToPassport()
                }
            )

            Spacer(Modifier.height(16.dp))
            TextField(
                value = uiState.promoCode,
                onValueChange = viewModel::onPromoChange,
                textStyle = orangeRegisterInputTextStyle(),
                placeholder = { Text("ПРОМОКОД", color = Color.White.copy(0.55f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.15f), RoundedCornerShape(28.dp)),
                colors = orangeFieldColors(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Получать рассылки", color = Color.White)
                Switch(
                    checked = uiState.newsletter,
                    onCheckedChange = viewModel::onNewsletterChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(0.45f),
                        uncheckedThumbColor = Color.White.copy(0.7f),
                        uncheckedTrackColor = Color.White.copy(0.25f)
                    )
                )
            }

            OrangeOutlineField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Пароль",
                error = uiState.fieldErr(uiState.passwordError),
                imeAction = ImeAction.Next,
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            )
            OrangeOutlineField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = "Подтвердите пароль",
                error = uiState.fieldErr(uiState.confirmPasswordError),
                imeAction = ImeAction.Done,
                onDone = {
                    focusManager.clearFocus()
                    viewModel.register()
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            )

            val legal = legalAnnotatedString()
            ClickableText(
                text = legal,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(0.85f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                onClick = { offset ->
                    legal.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        runCatching { uriHandler.openUri(it.item) }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onAcceptedLegalTermsChange(!uiState.acceptedLegalTerms) }
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = uiState.acceptedLegalTerms,
                    onCheckedChange = viewModel::onAcceptedLegalTermsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(0.45f),
                        uncheckedThumbColor = Color.White.copy(0.7f),
                        uncheckedTrackColor = Color.White.copy(0.25f)
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Я ознакомился и согласен с условиями",
                    color = Color.White.copy(0.92f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
            uiState.fieldErr(uiState.legalTermsError)?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFE0B2),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = {
                        typeMenuExpanded = false
                        focusManager.clearFocus()
                        viewModel.register()
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(0.25f),
                        contentColor = Color.White,
                        disabledContainerColor = Color.White.copy(0.12f)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("ЗАРЕГИСТРИРОВАТЬСЯ", fontWeight = FontWeight.Bold)
                    }
                }

                uiState.error?.let {
                    Text(
                        it,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Уже есть аккаунт?", color = Color.White.copy(0.85f))
                    TextButton(onClick = onNavigateToLogin) {
                        Text("Войти", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }
}

/** Палитра для полей на оранжевом фоне: без серого «surfaceVariant» и без белой error-плашки. */
private fun registerFormColorScheme(base: ColorScheme): ColorScheme =
    base.copy(
        surfaceVariant = Color.Transparent,
        onSurfaceVariant = Color.White.copy(0.82f),
        // onSurface не трогаем: выпадающие меню используют светлый surface и должны брать тёмный onSurface из темы.
        outline = Color.White.copy(0.5f),
        errorContainer = Color.Transparent,
        onErrorContainer = Color(0xFFFFE0B2)
    )

/** Ошибки полей только после первой попытки регистрации — до этого форма без «подсветки». */
private fun RegisterUiState.fieldErr(message: String?): String? =
    if (submitAttempted) message else null

private fun legalAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append("Нажимая кнопку «Зарегистрироваться», я подтверждаю, что ознакомился и соглашаюсь с условиями ")
    pushStringAnnotation("URL", AppConfig.USER_AGREEMENT_URL)
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Пользовательского соглашения")
    }
    pop()
    append(", а также подтверждаю ознакомление с ")
    pushStringAnnotation("URL", AppConfig.PRIVACY_URL)
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Политикой по обработке персональных данных")
    }
    pop()
    append(".")
}

@Composable
private fun SelectedClubSummary(
    club: ClubItem?,
    clubsLoadError: String?,
    clubError: String?,
    onChangeClub: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.14f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val img = when (club?.id) {
                "3" -> R.drawable.registration_club_kupera
                "1", "2" -> R.drawable.registration_club_mall
                else -> null
            }
            if (img != null) {
                Image(
                    painter = painterResource(img),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Ваш клуб", color = Color.White.copy(0.75f), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = club?.let { "${it.name}\n${it.address}" } ?: "Не выбран",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                clubsLoadError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color(0xFFFFE0B2), style = MaterialTheme.typography.bodySmall)
                }
                clubError?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color(0xFFFFE0B2), style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onChangeClub) {
                Text("Изменить", color = Color.White)
            }
        }
    }
}

@Composable
private fun PassportCard(
    summary: String,
    error: String?,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text("Мой паспорт", color = Color.White.copy(0.8f), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(summary, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
        }
        error?.let { Text(it, color = Color.White.copy(0.95f), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun OrangeOutlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = orangeRegisterInputTextStyle(),
            label = { Text(label, color = Color.White.copy(0.78f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = orangeFieldColors(),
        isError = error != null,
        supportingText = error?.let { { Text(it, color = Color(0xFFFFE0B2)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() }
        ),
        visualTransformation = visualTransformation,
        trailingIcon = trailing
    )
}

/** Поля на оранжевом фоне: белый текст/курсор; палитра темы не подменяет onSurface (нужно для DropdownMenu). */
@Composable
private fun orangeFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color.White.copy(alpha = 0.5f),
    errorTextColor = Color(0xFFFFE0B2),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.White.copy(alpha = 0.85f),
    unfocusedIndicatorColor = Color.White.copy(alpha = 0.55f),
    disabledIndicatorColor = Color.White.copy(alpha = 0.35f),
    errorIndicatorColor = Color(0xFFFFE0B2),
    focusedLeadingIconColor = Color.White,
    unfocusedLeadingIconColor = Color.White,
    focusedTrailingIconColor = Color.White,
    unfocusedTrailingIconColor = Color.White,
    cursorColor = Color.White,
    errorCursorColor = Color(0xFFFFE0B2)
)

@Composable
private fun orangeRegisterInputTextStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge.merge(TextStyle(color = Color.White))
