package com.fitnessclub.app.ui.screens.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEmailScreen(
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        TopAppBar(
            title = { Text("Давайте познакомимся!") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )
        Text(
            "Введите email — так мы поймём, не регистрировались ли вы раньше.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        TextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            isError = state.emailError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(12.dp),
        )
        state.emailError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.submitRegisterEmail(onContinue) },
            enabled = !state.emailCheckLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state.emailCheckLoading) {
                CircularProgressIndicator(Modifier.height(22.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Далее", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    state.emailTakenMaskedPhone?.let { masked ->
        AlertDialog(
            onDismissRequest = viewModel::dismissEmailTaken,
            title = { Text("Аккаунт уже есть") },
            text = {
                Text(
                    (state.error ?: "Аккаунт с этой почтой уже есть.") +
                        " Телефон в профиле: $masked. Обратитесь в поддержку, чтобы восстановить доступ.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mail = state.supportEmail
                        val phone = state.supportPhone
                        val intent = when {
                            !mail.isNullOrBlank() -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail"))
                            !phone.isNullOrBlank() -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            else -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                        }
                        runCatching { context.startActivity(intent) }
                    },
                ) { Text("Связаться с поддержкой") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissEmailTaken) {
                    Text("Ввести другой email")
                }
            },
        )
    }
}
