package com.fitnessclub.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.ui.components.GeneralFeedbackDialog
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToSecuritySettings: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settingsState by viewModel.uiState.collectAsState()
    var pushEnabled by remember { mutableStateOf(true) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var emailEnabled by remember { mutableStateOf(true) }
    var trainingReminders by remember { mutableStateOf(true) }
    var promoNotifications by remember { mutableStateOf(false) }
    var scheduleChanges by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        viewModel.refreshBiometricUi()
        onDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Notifications section
            SettingsSection(title = "Уведомления") {
                SwitchSettingItem(
                    icon = Icons.Default.Notifications,
                    title = "Push-уведомления",
                    subtitle = "Получать push-уведомления",
                    checked = pushEnabled,
                    onCheckedChange = { pushEnabled = it }
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.Email,
                    title = "Email-уведомления",
                    subtitle = "Получать уведомления на почту",
                    checked = emailEnabled,
                    onCheckedChange = { emailEnabled = it }
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SwitchSettingItem(
                    icon = Icons.Default.FitnessCenter,
                    title = "Напоминания о тренировках",
                    subtitle = "За 1 час до начала",
                    checked = trainingReminders,
                    onCheckedChange = { trainingReminders = it }
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.Schedule,
                    title = "Изменения в расписании",
                    subtitle = "Уведомлять об изменениях",
                    checked = scheduleChanges,
                    onCheckedChange = { scheduleChanges = it }
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.LocalOffer,
                    title = "Акции и предложения",
                    subtitle = "Специальные предложения клуба",
                    checked = promoNotifications,
                    onCheckedChange = { promoNotifications = it }
                )
            }
            
            // Security section
            SettingsSection(title = "Безопасность") {
                ClickableSettingItem(
                    icon = Icons.Default.Lock,
                    title = "Изменить пароль",
                    onClick = {
                        runCatching { uriHandler.openUri(AppConfig.FORGOT_PASSWORD_URL) }
                            .onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Не удалось открыть страницу восстановления")
                                }
                            }
                    }
                )

                SwitchSettingItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Биометрия",
                    subtitle = "Вход по отпечатку пальца",
                    checked = settingsState.biometricLoginEnabled,
                    onCheckedChange = { enabled ->
                        val act = context as? FragmentActivity
                        viewModel.onBiometricLoginSwitch(enabled, act) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                )

                ClickableSettingItem(
                    icon = Icons.Default.Security,
                    title = "Двухфакторная аутентификация",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("2FA появится в обновлении приложения")
                        }
                    }
                )
            }
            
            // App section
            SettingsSection(title = "Приложение") {
                ClickableSettingItem(
                    icon = Icons.Default.Language,
                    title = "Язык",
                    subtitle = "Русский",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Другие языки будут добавлены позже")
                        }
                    }
                )

                ClickableSettingItem(
                    icon = Icons.Default.Palette,
                    title = "Тема",
                    subtitle = "Системная",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Выбор темы — в следующем обновлении")
                        }
                    }
                )
                
                ClickableSettingItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Очистить кэш",
                    onClick = { viewModel.clearCache() }
                )
            }
            
            // Support section
            SettingsSection(title = "Поддержка") {
                ClickableSettingItem(
                    icon = Icons.Default.Help,
                    title = "Помощь",
                    onClick = onNavigateToHelp
                )
                
                ClickableSettingItem(
                    icon = Icons.Default.Feedback,
                    title = "Обратная связь",
                    subtitle = "Оцените работу клуба без контакта",
                    onClick = { showFeedbackDialog = true }
                )
                
                ClickableSettingItem(
                    icon = Icons.Default.Star,
                    title = "Оценить приложение",
                    onClick = { uriHandler.openUri(AppConfig.PLAY_STORE_URL) }
                )
                
                ClickableSettingItem(
                    icon = Icons.Default.Info,
                    title = "О приложении",
                    subtitle = "Версия 1.0.0",
                    onClick = onNavigateToAbout
                )
            }
            
            // Legal section
            SettingsSection(title = "Правовая информация") {
                ClickableSettingItem(
                    icon = Icons.Default.Description,
                    title = "Пользовательское соглашение",
                    onClick = { uriHandler.openUri(AppConfig.TERMS_URL) }
                )
                
                ClickableSettingItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Политика конфиденциальности",
                    onClick = { uriHandler.openUri(AppConfig.PRIVACY_URL) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    if (showFeedbackDialog) {
        GeneralFeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { rating, comment ->
                viewModel.submitFeedback(
                    rating = rating,
                    comment = comment,
                    onSuccess = { showFeedbackDialog = false },
                    onError = { /* Остаёмся в диалоге для повторной попытки */ }
                )
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(content = content)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ClickableSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
