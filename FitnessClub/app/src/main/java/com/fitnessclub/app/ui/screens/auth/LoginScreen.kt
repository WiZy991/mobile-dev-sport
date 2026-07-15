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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.auth.SberAuthDeepLinkBus
import com.fitnessclub.app.data.config.AppConfig

/** Фон входа в духе макета (терракота). */
private val LoginBackground = Color(0xFFD35400)
private val LoginSurfaceWhite = Color.White
private val SberButtonGreen = Color(0xFF21A038)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    startWithSber: Boolean = false,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

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
                is LoginEvent.OpenExternalUrl -> {
                    runCatching {
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, android.net.Uri.parse(event.url))
                    }.onFailure {
                        runCatching { uriHandler.openUri(event.url) }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        SberAuthDeepLinkBus.events.collect { uri ->
            viewModel.handleSberDeepLink(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBiometricOffer()
    }

    LaunchedEffect(startWithSber) {
        if (startWithSber) {
            viewModel.loginWithSberId()
        }
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
                text = "Доброзал",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Вход в аккаунт",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Войдите по email и паролю или через Сбер ID.",
                color = LoginSurfaceWhite.copy(0.92f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            LoginCredentialField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = "Email",
                error = uiState.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                leading = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = LoginBackground)
                },
            )
            Spacer(Modifier.height(12.dp))
            LoginCredentialField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Пароль",
                error = uiState.passwordError,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                leading = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = LoginBackground)
                },
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = null,
                            tint = LoginBackground,
                        )
                    }
                },
            )
            uiState.validationSummary?.let { summary ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF5D2E00).copy(0.55f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = summary,
                        color = Color(0xFFFFE0B2),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val hasFormIssue = uiState.validationSummary != null
            Button(
                onClick = { viewModel.login() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasFormIssue) {
                        LoginSurfaceWhite.copy(0.45f)
                    } else {
                        LoginSurfaceWhite
                    },
                    contentColor = LoginBackground,
                    disabledContainerColor = LoginSurfaceWhite.copy(0.5f),
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        color = LoginBackground,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Войти", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Регистрация",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClick = onNavigateToRegister)
                    .padding(vertical = 8.dp)
            )

            uiState.error?.let { err ->
                if (uiState.validationSummary == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = LoginSurfaceWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "или",
                color = LoginSurfaceWhite.copy(0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

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
                onClick = { viewModel.loginWithSberId() },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SberButtonGreen,
                    contentColor = LoginSurfaceWhite,
                    disabledContainerColor = SberButtonGreen.copy(0.5f),
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        color = LoginSurfaceWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Войти через Сбер ID", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            LoginBiometricButton(
                configured = uiState.biometricLoginConfigured,
                hardwareReady = uiState.biometricHardwareReady,
                isLoading = uiState.isLoading,
                activity = activity,
                onClick = { viewModel.onBiometricLoginClick(it) },
            )

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

@Composable
private fun LoginCredentialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            leadingIcon = leading,
            trailingIcon = trailing,
            isError = error != null,
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LoginSurfaceWhite,
                unfocusedContainerColor = LoginSurfaceWhite,
                disabledContainerColor = LoginSurfaceWhite.copy(0.7f),
                focusedTextColor = LoginBackground,
                unfocusedTextColor = LoginBackground,
                focusedLabelColor = LoginBackground.copy(0.75f),
                unfocusedLabelColor = LoginBackground.copy(0.6f),
                cursorColor = LoginBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        error?.let {
            Text(
                text = it,
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
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

