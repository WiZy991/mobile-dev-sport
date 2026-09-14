package com.fitnessclub.app.ui.screens.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.auth.SberAuthDeepLinkBus
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.ui.components.BrandHeader
import kotlin.math.roundToInt

/** Фон входа в духе макета (терракота). */
private val LoginBackground = Color(0xFFD35400)
private val LoginSurfaceWhite = Color.White
private val SberButtonGreen = Color(0xFF21A038)

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    startWithSber: Boolean = false,
    onNavigateToPhoneRegister: () -> Unit = {},
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var sberLaunchConsumed by remember { mutableStateOf(false) }
    var sberAuthorizeUrl by remember { mutableStateOf<String?>(null) }
    val otpShake = remember { Animatable(0f) }

    LaunchedEffect(uiState.otpShakeNonce) {
        if (uiState.otpShakeNonce == 0) return@LaunchedEffect
        listOf(-18f, 18f, -12f, 12f, -8f, 8f, 0f).forEach { target ->
            otpShake.animateTo(target, animationSpec = tween(40))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.Success -> {
                    event.welcomeMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                    onLoginSuccess()
                }
                is LoginEvent.NeedPhoneRegistration -> onNavigateToPhoneRegister()
                is LoginEvent.OpenExternalUrl -> {
                    // Только внутри этого APK — не Custom Tabs (иначе callback уезжает в Академию).
                    sberAuthorizeUrl = event.url
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
        if (startWithSber && !sberLaunchConsumed) {
            sberLaunchConsumed = true
            viewModel.setWelcomeAfterSberLogin(true)
            viewModel.loginWithSberId()
        }
    }

    sberAuthorizeUrl?.let { url ->
        SberAuthWebDialog(
            authorizeUrl = url,
            onCallback = { uri ->
                sberAuthorizeUrl = null
                viewModel.handleSberDeepLink(uri)
            },
            onDismiss = { sberAuthorizeUrl = null },
        )
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
            // Иностранные номера и полноценный словесный логотип — не в этом релизе.
            BrandHeader(
                brandName = Brand.name,
                subtitle = null,
            )
            Spacer(Modifier.height(24.dp))

            if (uiState.otpStep == LoginOtpStep.PHONE) {
                Text(
                    text = "Войти или создать аккаунт",
                    color = LoginSurfaceWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Пришлём код подтверждения",
                    color = LoginSurfaceWhite.copy(0.92f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                LoginCredentialField(
                    value = russianPhoneFieldValue(uiState.phoneNationalDigits),
                    onValueChange = { viewModel.onPhoneChange(it.text) },
                    label = "Телефон",
                    error = uiState.phoneError,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.requestOtp() },
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.requestOtp() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LoginSurfaceWhite, contentColor = LoginBackground),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = LoginBackground, strokeWidth = 2.dp)
                    } else {
                        Text("Продолжить", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = {
                        openSupportContact(context, uiState.supportEmail, uiState.supportPhone)
                    },
                ) {
                    Text(
                        "Связаться с поддержкой",
                        color = LoginSurfaceWhite.copy(0.92f),
                        textDecoration = TextDecoration.Underline,
                    )
                }
                TextButton(onClick = { viewModel.toggleEmailLogin() }) {
                    Text(
                        if (uiState.showEmailLogin) "Скрыть другие варианты входа" else "Другие варианты входа",
                        color = LoginSurfaceWhite,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            } else {
                Text(
                    text = "Код подтверждения",
                    color = LoginSurfaceWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.otpInstruction?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = LoginSurfaceWhite.copy(0.9f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                if (uiState.otpChannel == "max") {
                    uiState.otpDeeplink?.let { link ->
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }
                        }) {
                            Text(
                                "Открыть Max",
                                color = LoginSurfaceWhite,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.offset { IntOffset(otpShake.value.roundToInt(), 0) }) {
                    LoginCredentialField(
                        value = uiState.otpCode,
                        onValueChange = viewModel::onOtpCodeChange,
                        label = "Код",
                        error = uiState.otpError,
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                        onImeAction = { viewModel.verifyOtp() },
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.requestOtp() },
                    enabled = uiState.resendSecondsLeft == 0 && !uiState.isLoading,
                ) {
                    Text(
                        if (uiState.resendSecondsLeft > 0) "Отправить повторно (${uiState.resendSecondsLeft})"
                        else "Отправить повторно",
                        color = LoginSurfaceWhite.copy(if (uiState.resendSecondsLeft == 0) 1f else 0.5f),
                        textDecoration = TextDecoration.Underline,
                    )
                }
                TextButton(onClick = { viewModel.backToPhoneStep() }) {
                    Text(
                        "Назад",
                        color = LoginSurfaceWhite.copy(0.85f),
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = LoginSurfaceWhite, textAlign = TextAlign.Center)
            }

            if (uiState.otpStep == LoginOtpStep.PHONE) {
            AnimatedVisibility(
                visible = uiState.showEmailLogin,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
            Column {
            Spacer(Modifier.height(8.dp))

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
                if (uiState.loginHintCode == "password_not_set") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LoginSurfaceWhite),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = summary,
                                color = LoginBackground,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = { viewModel.loginWithSberId() },
                                enabled = !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SberButtonGreen,
                                    contentColor = LoginSurfaceWhite,
                                ),
                            ) {
                                Text("Войти через Сбер ID", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
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
                text = "или",
                color = LoginSurfaceWhite.copy(0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            uiState.error?.let { err ->
                if (uiState.validationSummary == null) {
                    Text(
                        text = err,
                        color = LoginSurfaceWhite,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

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
            }
            }
            }

            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
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
    leading: @Composable (() -> Unit)? = null,
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

@Composable
private fun LoginCredentialField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
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

private fun openSupportContact(context: android.content.Context, email: String?, phone: String?) {
    val intent = when {
        !email.isNullOrBlank() -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
        !phone.isNullOrBlank() -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        else -> null
    }
    intent?.let { runCatching { context.startActivity(it) } }
}

