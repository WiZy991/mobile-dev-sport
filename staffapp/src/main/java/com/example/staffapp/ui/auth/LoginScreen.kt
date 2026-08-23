package com.example.staffapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.staffapp.legal.StaffLegalPdf
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSecondaryButton
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

data class RoleOptionUi(val label: String, val role: String)

data class LoginUiState(
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val selectedRole: RoleOptionUi? = null,
    val roles: List<RoleOptionUi> = emptyList(),
    val configSummary: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRoleSelected: (RoleOptionUi) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onOpenLegalPdf: (StaffLegalPdf) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StaffPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Доброзал",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Приложение для специалистов и сотрудников Клуба",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = { Text("Имя (для регистрации)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text("Пароль") },
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
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(
                        text = "Войдите в приложение или создайте новый аккаунт, чтобы получить доступ к функциям для специалистов или сотрудников Клуба.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StaffOnSurfaceVariant,
                    )
                    val legalText = remember {
                        buildAnnotatedString {
                            append("Продолжая использовать приложение, Вы принимаете условия ")
                            pushStringAnnotation("PDF", StaffLegalPdf.USER_AGREEMENT.name)
                            withStyle(
                                SpanStyle(
                                    color = StaffPrimary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append("Пользовательского соглашения")
                            }
                            pop()
                            append(" и подтверждаете ознакомление с ")
                            pushStringAnnotation("PDF", StaffLegalPdf.PRIVACY.name)
                            withStyle(
                                SpanStyle(
                                    color = StaffPrimary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append("Политикой конфиденциальности")
                            }
                            pop()
                            append(".")
                        }
                    }
                    ClickableText(
                        text = legalText,
                        style = MaterialTheme.typography.bodySmall.copy(color = StaffOnSurfaceVariant),
                        onClick = { offset ->
                            legalText.getStringAnnotations("PDF", offset, offset)
                                .firstOrNull()
                                ?.let { ann ->
                                    StaffLegalPdf.entries
                                        .find { it.name == ann.item }
                                        ?.let(onOpenLegalPdf)
                                }
                        },
                    )
                    if (state.configSummary.isNotBlank()) {
                        Text(
                            text = state.configSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = StaffOnSurfaceVariant,
                        )
                    }
                    StaffPrimaryButton(
                        text = if (state.isLoading) "Вход..." else "Войти",
                        onClick = onLogin,
                        enabled = !state.isLoading,
                    )
                    StaffSecondaryButton(
                        text = "Зарегистрироваться",
                        onClick = onRegister,
                        enabled = !state.isLoading,
                    )
                    if (state.isLoading && state.statusMessage != null) {
                        Text(
                            state.statusMessage,
                            color = StaffOnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    state.errorMessage?.let {
                        StaffErrorState(message = it)
                    }
                }
            }
        }
    }
}
