package com.fitnessclub.app.ui.screens.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    viewModel: HelpViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Помощь") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Часто задаваемые вопросы и инструкции — на странице поддержки.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { uriHandler.openUri(AppConfig.HELP_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть страницу помощи")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Обращение в клуб",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            if (!uiState.hasUserProfile) {
                OutlinedTextField(
                    value = uiState.contactEmail,
                    onValueChange = viewModel::setContactEmail,
                    label = { Text("Email для ответа") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = uiState.contactEmail,
                    onValueChange = viewModel::setContactEmail,
                    label = { Text("Email для ответа (необязательно)") },
                    placeholder = { Text("По умолчанию — из профиля") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ExposedDropdownMenuBox(
                expanded = uiState.categoryMenuExpanded,
                onExpandedChange = viewModel::setCategoryMenuExpanded,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = SupportCategories.label(uiState.categoryApi),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тематика") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.categoryMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = uiState.categoryMenuExpanded,
                    onDismissRequest = { viewModel.setCategoryMenuExpanded(false) },
                    modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
                ) {
                    SupportCategories.options.forEach { (api, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { viewModel.setCategory(api) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.subject,
                onValueChange = viewModel::setSubject,
                label = { Text("Тема") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.message,
                onValueChange = viewModel::setMessage,
                label = { Text("Сообщение") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            uiState.successMessage?.let { ok ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(text = ok, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { viewModel.dismissSuccess() }) {
                            Text("Понятно")
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.submit() },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSubmitting) "Отправка…" else "Отправить обращение")
            }
        }
    }
}
