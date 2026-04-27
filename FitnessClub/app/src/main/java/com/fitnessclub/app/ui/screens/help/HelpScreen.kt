package com.fitnessclub.app.ui.screens.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
<<<<<<< HEAD
=======
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
>>>>>>> a188090 (update)
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
<<<<<<< HEAD
import androidx.compose.foundation.layout.padding
=======
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
>>>>>>> a188090 (update)
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
<<<<<<< HEAD
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
=======
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
>>>>>>> a188090 (update)
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
<<<<<<< HEAD
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
=======
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
>>>>>>> a188090 (update)
import com.fitnessclub.app.data.config.AppConfig
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.launch

private val supportCategories = listOf(
    "question" to "Вопрос",
    "technical" to "Техническая проблема",
    "billing" to "Оплата / абонемент",
    "complaint" to "Жалоба",
    "suggestion" to "Предложение",
    "other" to "Другое"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(
<<<<<<< HEAD
    viewModel: HelpViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()
=======
    viewModel: HelpViewModel,
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("question") }
    var contactEmail by remember { mutableStateOf("") }

    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            snackbarHostState.showSnackbar("Обращение отправлено. Мы свяжемся с вами.")
            subject = ""
            message = ""
            contactEmail = ""
            viewModel.consumeSuccess()
        }
    }

    LaunchedEffect(uiState.sendError) {
        uiState.sendError?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }
>>>>>>> a188090 (update)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
<<<<<<< HEAD
                text = "Часто задаваемые вопросы и инструкции — на странице поддержки.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
=======
                text = "Часто задаваемые вопросы и инструкции — на странице поддержки клуба.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
>>>>>>> a188090 (update)
            Button(
                onClick = { uriHandler.openUri(AppConfig.HELP_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть страницу помощи")
            }

<<<<<<< HEAD
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Обращение в клуб",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
=======
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Обращение в поддержку",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Заполните форму — сообщение попадёт в CRM клуба в раздел «Обращения из приложения».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тема", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Кратко, о чём обращение") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Категория", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        supportCategories.forEach { (code, label) ->
                            FilterChip(
                                selected = category == code,
                                onClick = { category = code },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Сообщение", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        minLines = 5,
                        placeholder = { Text("Опишите ситуацию подробнее") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Контактный e-mail (необязательно)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = { contactEmail = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Если ответ нужен на другой адрес") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val sub = subject.trim()
                            val msg = message.trim()
                            when {
                                sub.isEmpty() -> scope.launch {
                                    snackbarHostState.showSnackbar("Укажите тему обращения")
                                }
                                msg.length < 5 -> scope.launch {
                                    snackbarHostState.showSnackbar("Сообщение слишком короткое")
                                }
                                else -> viewModel.submitSupportTicket(
                                    subject = sub,
                                    message = msg,
                                    category = category,
                                    contactEmail = contactEmail.trim().ifEmpty { null },
                                    onDone = {},
                                    onFail = {}
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSending
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (uiState.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Отправить в клуб")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "По срочным вопросам можно позвонить в клуб — телефон указан в разделе «О клубе».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
>>>>>>> a188090 (update)
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
