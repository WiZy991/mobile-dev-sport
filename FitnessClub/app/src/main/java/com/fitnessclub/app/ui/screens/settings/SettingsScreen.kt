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
import androidx.compose.runtime.LaunchedEffect
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
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.config.LegalLinks
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.data.config.AppDistribution
import com.fitnessclub.app.data.local.AppLanguage
import com.fitnessclub.app.data.local.ThemeMode
import com.fitnessclub.app.ui.components.GeneralFeedbackDialog
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToSecuritySettings: () -> Unit = {},
    onNavigateToNetworkInfo: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onOpenLegalDocument: (LegalDocumentType) -> Unit = {},
    onOpenLegalPdf: (LegalPdfAsset) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settingsState by viewModel.uiState.collectAsState()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val notificationSettings = settingsState.notificationSettings
    val openLegal: (LegalDocumentType) -> Unit = { type ->
        LegalLinks.open(type, onOpenLegalPdf, onOpenLegalDocument)
    }

    LaunchedEffect(settingsState.notificationsError) {
        settingsState.notificationsError?.let { snackbarHostState.showSnackbar(it) }
    }

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
                    checked = notificationSettings.pushEnabled,
                    enabled = !settingsState.notificationsLoading && !settingsState.notificationsSaving,
                    onCheckedChange = viewModel::setPushEnabled
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.Email,
                    title = "Email-уведомления",
                    subtitle = "Получать уведомления на почту",
                    checked = notificationSettings.emailEnabled,
                    enabled = !settingsState.notificationsLoading && !settingsState.notificationsSaving,
                    onCheckedChange = viewModel::setEmailEnabled
                )
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                SwitchSettingItem(
                    icon = Icons.Default.FitnessCenter,
                    title = "Напоминания о тренировках",
                    subtitle = "За 1 час до начала",
                    checked = notificationSettings.trainingReminders,
                    enabled = !settingsState.notificationsLoading && !settingsState.notificationsSaving,
                    onCheckedChange = viewModel::setTrainingReminders
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.Schedule,
                    title = "Изменения в расписании",
                    subtitle = "Уведомлять об изменениях",
                    checked = notificationSettings.scheduleChanges,
                    enabled = !settingsState.notificationsLoading && !settingsState.notificationsSaving,
                    onCheckedChange = viewModel::setScheduleChanges
                )
                
                SwitchSettingItem(
                    icon = Icons.Default.LocalOffer,
                    title = "Акции и предложения",
                    subtitle = "Специальные предложения клуба",
                    checked = notificationSettings.promoNotifications,
                    enabled = !settingsState.notificationsLoading && !settingsState.notificationsSaving,
                    onCheckedChange = viewModel::setPromoNotifications
                )
            }
            
            // Security section
            SettingsSection(title = "Безопасность") {
                ClickableSettingItem(
                    icon = Icons.Default.Lock,
                    title = "Изменить пароль",
                    onClick = onNavigateToChangePassword
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

            }
            
            // App section
            SettingsSection(title = "Приложение") {
                ClickableSettingItem(
                    icon = Icons.Default.Language,
                    title = "Язык",
                    subtitle = settingsState.appLanguage.label,
                    onClick = { showLanguageDialog = true }
                )

                ClickableSettingItem(
                    icon = Icons.Default.Palette,
                    title = "Тема",
                    subtitle = when (settingsState.themeMode) {
                        ThemeMode.SYSTEM -> "Системная"
                        ThemeMode.LIGHT -> "Светлая"
                        ThemeMode.DARK -> "Тёмная"
                    },
                    onClick = { showThemeDialog = true }
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
                    icon = Icons.Default.Info,
                    title = "О сети и контакты",
                    subtitle = "О Доброзал, обратная связь, оценка приложения",
                    onClick = onNavigateToNetworkInfo
                )
            }
            
            // Legal section
            SettingsSection(title = "Правовая информация") {
                ClickableSettingItem(
                    icon = Icons.Default.AccountBalance,
                    title = "Реквизиты",
                    onClick = { openLegal(LegalDocumentType.REQUISITES) }
                )

                ClickableSettingItem(
                    icon = Icons.Default.Description,
                    title = "Пользовательское соглашение",
                    onClick = { onOpenLegalPdf(LegalPdfAsset.USER_AGREEMENT) }
                )

                ClickableSettingItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Политика конфиденциальности",
                    onClick = { onOpenLegalPdf(LegalPdfAsset.PRIVACY_POLICY) }
                )

                ClickableSettingItem(
                    icon = Icons.Default.Article,
                    title = "Договор с Клубом",
                    onClick = { onOpenLegalPdf(LegalPdfAsset.DOBROZAL_OFFER) }
                )

                ClickableSettingItem(
                    icon = Icons.Default.VerifiedUser,
                    title = "Политика обработки и защиты персональных данных Клуба",
                    onClick = { onOpenLegalPdf(LegalPdfAsset.PRIVACY_POLICY) }
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

    if (showLanguageDialog) {
        AppLanguageDialog(
            selected = settingsState.appLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { selected ->
                viewModel.setAppLanguage(selected)
                showLanguageDialog = false
            },
        )
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            selected = settingsState.themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { selected ->
                viewModel.setThemeMode(selected)
                showThemeDialog = false
            },
        )
    }

}

@Composable
private fun AppLanguageDialog(
    selected: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Язык приложения") },
        text = {
            Column {
                Text(
                    text = "Сейчас полностью поддерживается русский язык. " +
                        "Мультиязычность добавим в следующих релизах.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = language == selected,
                            onClick = { onSelect(language) },
                        )
                        Text(
                            text = language.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit,
) {
    val labels = mapOf(
        ThemeMode.SYSTEM to "Системная",
        ThemeMode.LIGHT to "Светлая",
        ThemeMode.DARK to "Тёмная",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Тема приложения") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            onClick = { onSelect(mode) },
                        )
                        Text(
                            text = labels[mode].orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable { onCheckedChange(!checked) }
                else Modifier
            )
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
            onCheckedChange = onCheckedChange,
            enabled = enabled,
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
