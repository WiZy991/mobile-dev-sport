package com.fitnessclub.app.ui.screens.networkinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.config.AppDistribution
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.fitnessclub.app.ui.screens.help.HelpViewModel
import com.fitnessclub.app.ui.screens.help.SupportCategories
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkInfoScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetworkInfoViewModel = hiltViewModel(),
    helpViewModel: HelpViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val helpState by helpViewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val storeRatingOptions = AppDistribution.storeRatingOptions(context)
    val storeRatingHint = AppDistribution.storeRatingHint(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О сети и контакты") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading && uiState.clubName.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "О сети ${uiState.clubName}") {
                Text(
                    text = uiState.aboutText.ifBlank {
                        "Сеть фитнес-клубов «${uiState.clubName}». Расписание, абонементы и сервисы — в мобильном приложении."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Версия приложения ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Контакты") {
                ContactRow(
                    icon = { Icon(Icons.Default.Phone, null, Modifier.size(20.dp)) },
                    label = "Телефон",
                    value = uiState.phone,
                    onClick = uiState.phone?.let { phone ->
                        { runCatching { uriHandler.openUri("tel:$phone") } }
                    },
                )
                ContactRow(
                    icon = { Icon(Icons.Default.Email, null, Modifier.size(20.dp)) },
                    label = "Email",
                    value = uiState.email,
                    onClick = uiState.email?.let { mail ->
                        { runCatching { uriHandler.openUri("mailto:$mail") } }
                    },
                )
                ContactRow(
                    icon = { Icon(Icons.Default.Language, null, Modifier.size(20.dp)) },
                    label = "Сайт",
                    value = uiState.website,
                    onClick = uiState.website?.let { url ->
                        { runCatching { uriHandler.openUri(url) } }
                    },
                )
                uiState.workingHours?.takeIf { it.isNotBlank() }?.let { hours ->
                    Text(
                        text = "Режим работы: $hours",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                uiState.address?.takeIf { it.isNotBlank() }?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.socialVk != null || uiState.socialTelegram != null) {
                SectionCard(title = "Соцсети") {
                    uiState.socialVk?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                            Text("ВКонтакте")
                        }
                    }
                    uiState.socialTelegram?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                            Text("Telegram")
                        }
                    }
                }
            }

            SectionCard(title = "Обратная связь") {
                NetworkFeedbackForm(helpState = helpState, helpViewModel = helpViewModel)
            }

            SectionCard(title = "Оценить приложение") {
                storeRatingHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                storeRatingOptions.forEach { option ->
                    val isPrimary = storeRatingOptions.size == 1
                    if (isPrimary) {
                        Button(
                            onClick = { runCatching { uriHandler.openUri(option.url) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option.label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { runCatching { uriHandler.openUri(option.url) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option.label)
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ContactRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String?,
    onClick: (() -> Unit)?,
) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClick != null) {
                TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(value)
                }
            } else {
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkFeedbackForm(
    helpState: com.fitnessclub.app.ui.screens.help.HelpUiState,
    helpViewModel: HelpViewModel,
) {
    if (!helpState.hasUserProfile) {
        OutlinedTextField(
            value = helpState.contactEmail,
            onValueChange = helpViewModel::setContactEmail,
            label = { Text("Email для ответа") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = helpState.contactEmail,
            onValueChange = helpViewModel::setContactEmail,
            label = { Text("Email для ответа (необязательно)") },
            placeholder = { Text("По умолчанию — из профиля") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ExposedDropdownMenuBox(
        expanded = helpState.categoryMenuExpanded,
        onExpandedChange = helpViewModel::setCategoryMenuExpanded,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = SupportCategories.label(helpState.categoryApi),
            onValueChange = {},
            readOnly = true,
            label = { Text("Тематика") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = helpState.categoryMenuExpanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = helpState.categoryMenuExpanded,
            onDismissRequest = { helpViewModel.setCategoryMenuExpanded(false) },
            modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true),
        ) {
            SupportCategories.options.forEach { (api, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { helpViewModel.setCategory(api) },
                )
            }
        }
    }

    OutlinedTextField(
        value = helpState.subject,
        onValueChange = helpViewModel::setSubject,
        label = { Text("Тема") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = helpState.message,
        onValueChange = helpViewModel::setMessage,
        label = { Text("Сообщение") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )

    helpState.errorMessage?.let { err ->
        Text(
            text = err,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    helpState.successMessage?.let { ok ->
        Text(
            text = ok,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Button(
        onClick = { helpViewModel.submit() },
        enabled = !helpState.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (helpState.isSubmitting) "Отправка…" else "Отправить обращение")
    }
}
