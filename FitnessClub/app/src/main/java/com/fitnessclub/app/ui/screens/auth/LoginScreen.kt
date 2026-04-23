package com.fitnessclub.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.config.AppConfig

/** Фон входа в духе макета (терракота). */
private val LoginBackground = Color(0xFFD35400)
private val LoginButtonColor = Color(0xFFB84A18)
private val LoginSurfaceWhite = Color.White

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    var passwordVisible by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.Success -> {
                    val rt = event.refreshToReEncryptForBiometric
                    if (!rt.isNullOrBlank() && activity != null) {
                        viewModel.reEncryptBiometricAfterPasswordLogin(activity, rt) {
                            onLoginSuccess()
                        }
                    } else {
                        onLoginSuccess()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBiometricOffer()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ВХОД",
                color = LoginSurfaceWhite.copy(0.9f),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "GYMroom",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Какой ваш номер телефона?",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Вы уже являетесь клиентом клуба? Тогда просто подтвердите ваш номер телефона. Если нет, то пройдите регистрацию.",
                color = LoginSurfaceWhite.copy(0.92f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🇷🇺",
                    fontSize = 22.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                TextField(
                    value = uiState.phoneNationalDigits,
                    onValueChange = viewModel::onPhoneChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "+7 (___) ___-__-__",
                            color = LoginSurfaceWhite.copy(0.45f)
                        )
                    },
                    visualTransformation = remember { RussianPhoneVisualTransformation() },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = if (uiState.credentialsStep) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (!uiState.credentialsStep) viewModel.continueFromPhone()
                        },
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true,
                    colors = loginFieldColors(),
                    isError = uiState.phoneError != null,
                    supportingText = uiState.phoneError?.let {
                        {
                            Text(it, color = LoginSurfaceWhite)
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.credentialsStep,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Вход по email и паролю, указанным при регистрации.",
                        color = LoginSurfaceWhite.copy(0.88f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("E-mail", color = LoginSurfaceWhite.copy(0.85f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        colors = loginFieldColors(),
                        isError = uiState.emailError != null,
                        supportingText = uiState.emailError?.let {
                            { Text(it, color = LoginSurfaceWhite) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChange,
                        label = { Text("Пароль", color = LoginSurfaceWhite.copy(0.85f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = null,
                                    tint = LoginSurfaceWhite
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.login()
                            }
                        ),
                        colors = loginFieldColors(),
                        isError = uiState.passwordError != null,
                        supportingText = uiState.passwordError?.let {
                            { Text(it, color = LoginSurfaceWhite) }
                        }
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Регистрация",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClick = onNavigateToRegister)
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            val legal = loginLegalAnnotatedString()
            ClickableText(
                text = legal,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = LoginSurfaceWhite.copy(0.88f),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { offset ->
                    legal.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                        runCatching { uriHandler.openUri(it.item) }
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (!uiState.credentialsStep) {
                        viewModel.continueFromPhone()
                    } else {
                        viewModel.login()
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LoginButtonColor,
                    contentColor = LoginSurfaceWhite,
                    disabledContainerColor = LoginButtonColor.copy(0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        color = LoginSurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (uiState.credentialsStep) "ВОЙТИ" else "ПРОДОЛЖИТЬ",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            LoginBiometricButton(
                configured = uiState.biometricLoginConfigured,
                hardwareReady = uiState.biometricHardwareReady,
                isLoading = uiState.isLoading,
                activity = activity,
                onClick = { viewModel.onBiometricLoginClick(it) },
            )

            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = LoginSurfaceWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LoginBiometricButton(
    configured: Boolean,
    hardwareReady: Boolean,
    isLoading: Boolean,
    activity: FragmentActivity?,
    onClick: (FragmentActivity) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedButton(
            onClick = { activity?.let(onClick) },
            enabled = !isLoading && activity != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LoginSurfaceWhite.copy(0.92f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LoginSurfaceWhite,
                disabledContentColor = LoginSurfaceWhite.copy(0.45f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text("Войти по отпечатку пальца", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                !configured ->
                    "После первого входа: Настройки → Безопасность → включите биометрию."
                configured && !hardwareReady ->
                    "Добавьте отпечаток в системных настройках телефона."
                else ->
                    "Быстрый вход без пароля, если биометрия уже включена в приложении."
            },
            color = LoginSurfaceWhite.copy(0.82f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun loginLegalAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append("Продолжая, вы принимаете ")
    pushStringAnnotation("URL", AppConfig.TERMS_URL)
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Правила использования")
    }
    pop()
    append(", ")
    pushStringAnnotation("URL", AppConfig.PRIVACY_URL)
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Политику конфиденциальности")
    }
    pop()
    append(" и соглашаетесь на обработку персональных данных")
}

@Composable
private fun loginFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = LoginSurfaceWhite,
    unfocusedIndicatorColor = LoginSurfaceWhite.copy(0.55f),
    focusedTextColor = LoginSurfaceWhite,
    unfocusedTextColor = LoginSurfaceWhite,
    focusedLabelColor = LoginSurfaceWhite.copy(0.85f),
    unfocusedLabelColor = LoginSurfaceWhite.copy(0.75f),
    cursorColor = LoginSurfaceWhite,
    focusedPlaceholderColor = LoginSurfaceWhite.copy(0.45f),
    unfocusedPlaceholderColor = LoginSurfaceWhite.copy(0.45f),
    errorIndicatorColor = LoginSurfaceWhite,
    errorSupportingTextColor = LoginSurfaceWhite,
    errorLabelColor = LoginSurfaceWhite
)
