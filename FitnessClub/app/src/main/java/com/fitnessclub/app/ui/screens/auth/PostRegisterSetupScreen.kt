package com.fitnessclub.app.ui.screens.auth

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fitnessclub.app.data.local.BiometricLoginCoordinator
import com.fitnessclub.app.data.local.BiometricLoginStore
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.ui.theme.Primary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val STEP_NOTIFICATIONS = 0
private const val STEP_BIOMETRIC = 1

/**
 * После успешной регистрации:
 * 1) системный запрос уведомлений (Android 13+),
 * 2) предложение включить биометрию (системный BiometricPrompt).
 */
@Composable
fun PostRegisterSetupScreen(
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PostRegisterSetupEntryPoint::class.java,
        )
    }
    val biometricStore = entryPoint.biometricLoginStore()
    val tokenManager = entryPoint.tokenManager()

    var step by remember { mutableIntStateOf(STEP_NOTIFICATIONS) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var biometricPromptShown by remember { mutableStateOf(false) }
    // Пересчитываем на шаге биометрии (после регистрации чужой отпечаток уже сброшен).
    var alreadyEnabled by remember { mutableStateOf(false) }
    val canBiometric = remember { biometricStore.canUseDeviceBiometric() }

    fun goToBiometricOrFinish() {
        if (canBiometric) {
            alreadyEnabled = biometricStore.hasStoredCredential()
            step = STEP_BIOMETRIC
        } else {
            onFinished()
        }
    }

    fun launchBiometricSetup(fromButton: Boolean = false) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            statusMessage = "Не удалось открыть окно биометрии"
            return
        }
        scope.launch {
            val rt = tokenManager.getRefreshToken()
            if (rt.isNullOrBlank()) {
                statusMessage = "Сессия не готова — настройте позже в профиле → Настройки"
                return@launch
            }
            val userId = tokenManager.getUser().first()?.id
            BiometricLoginCoordinator.startEncryptPrompt(
                activity,
                biometricStore,
                rt,
                userId = userId,
            ) { ok, err ->
                if (ok) {
                    onFinished()
                } else if (!err.isNullOrBlank()) {
                    statusMessage = err
                } else if (fromButton) {
                    // Отмена — остаёмся на экране
                }
            }
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        goToBiometricOrFinish()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            goToBiometricOrFinish()
            return@LaunchedEffect
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            goToBiometricOrFinish()
        } else {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Системный диалог биометрии сразу после уведомлений (с паузой — Android часто глотает второй prompt).
    LaunchedEffect(step) {
        if (step != STEP_BIOMETRIC || biometricPromptShown) return@LaunchedEffect
        if (alreadyEnabled) return@LaunchedEffect
        if (!canBiometric) return@LaunchedEffect
        biometricPromptShown = true
        delay(400)
        launchBiometricSetup(fromButton = false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            STEP_NOTIFICATIONS -> {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "Разрешить уведомления?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Мы пришлём напоминания о тренировках и важные сообщения клуба.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            goToBiometricOrFinish()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Разрешить")
                }
                TextButton(onClick = { goToBiometricOrFinish() }) {
                    Text("Не сейчас")
                }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (alreadyEnabled) "Вход по отпечатку уже включён" else "Вход по отпечатку",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (alreadyEnabled) {
                                "Можно продолжить — в следующий раз войдёте без пароля."
                            } else {
                                "Подтвердите отпечаток в системном окне — в следующий раз войдёте без пароля."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        statusMessage?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        if (alreadyEnabled) {
                            Button(
                                onClick = onFinished,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Продолжить")
                            }
                        } else {
                            Button(
                                onClick = {
                                    statusMessage = null
                                    launchBiometricSetup(fromButton = true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Настроить")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onFinished,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Пропустить")
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is Activity -> null
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PostRegisterSetupEntryPoint {
    fun biometricLoginStore(): BiometricLoginStore
    fun tokenManager(): TokenManager
}
