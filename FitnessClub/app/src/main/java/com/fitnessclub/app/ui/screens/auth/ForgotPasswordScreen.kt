package com.fitnessclub.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.ui.components.BrandHeader

private val LoginBackground = Color(0xFFD35400)
private val LoginSurfaceWhite = Color.White

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LoginBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandHeader(brandName = Brand.name, subtitle = null)
            Spacer(Modifier.height(24.dp))
            Text(
                text = if (uiState.done) "Пароль обновлён" else "Восстановление пароля",
                color = LoginSurfaceWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (uiState.done) {
                Text(
                    uiState.info ?: "Пароль обновлён. Войдите с новым паролем.",
                    color = LoginSurfaceWhite,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginSurfaceWhite,
                        contentColor = LoginBackground,
                    ),
                ) {
                    Text("Войти", fontWeight = FontWeight.Bold)
                }
            } else if (uiState.step == ForgotPasswordStep.EMAIL) {
                Text(
                    "Укажите email — пришлём код для нового пароля.",
                    color = LoginSurfaceWhite.copy(0.92f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ForgotField(
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.sendCode() },
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.sendCode() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginSurfaceWhite,
                        contentColor = LoginBackground,
                    ),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = LoginBackground, strokeWidth = 2.dp)
                    } else {
                        Text("Отправить код", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                uiState.info?.let {
                    Text(it, color = LoginSurfaceWhite.copy(0.92f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                }
                ForgotField(
                    value = uiState.code,
                    onValueChange = viewModel::onCodeChange,
                    label = "Код из письма",
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                )
                Spacer(Modifier.height(12.dp))
                ForgotField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Новый пароль",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(12.dp))
                ForgotField(
                    value = uiState.confirm,
                    onValueChange = viewModel::onConfirmChange,
                    label = "Повторите пароль",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeAction = { viewModel.resetPassword() },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                        color = LoginSurfaceWhite,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.resetPassword() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LoginSurfaceWhite,
                        contentColor = LoginBackground,
                    ),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = LoginBackground, strokeWidth = 2.dp)
                    } else {
                        Text("Сохранить пароль", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = { viewModel.backToEmail() }) {
                    Text("Назад", color = LoginSurfaceWhite, textDecoration = TextDecoration.Underline)
                }
            }
            uiState.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = LoginSurfaceWhite, textAlign = TextAlign.Center)
            }
            if (!uiState.done) {
                TextButton(onClick = onBack) {
                    Text("Ко входу", color = LoginSurfaceWhite, textDecoration = TextDecoration.Underline)
                }
            }
        }
    }
}

@Composable
private fun ForgotField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LoginSurfaceWhite,
            unfocusedContainerColor = LoginSurfaceWhite,
            disabledContainerColor = LoginSurfaceWhite,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = LoginBackground,
            focusedLabelColor = LoginBackground,
            unfocusedLabelColor = LoginBackground.copy(0.7f),
        ),
    )
}
