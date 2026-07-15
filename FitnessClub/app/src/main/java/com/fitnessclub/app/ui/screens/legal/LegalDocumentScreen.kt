package com.fitnessclub.app.ui.screens.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.config.LegalDocumentType
import com.fitnessclub.app.data.model.LegalDocumentField
import com.fitnessclub.app.ui.theme.Primary

private val LegalDocumentTextColor = Color(0xFF1A1A1A)
private val LegalDocumentMutedColor = Color(0xFF666666)
private val LegalDocumentBackground = Color.White
private val LegalDocumentCardBackground = Color(0xFFF5F5F5)

/** Нативный экран правового документа (текст из API, без сайта). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    document: LegalDocumentType,
    onNavigateBack: () -> Unit,
    viewModel: LegalDocumentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(document) {
        viewModel.load(document)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title.ifBlank { document.title }) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LegalDocumentBackground)
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(uiState.error!!, style = MaterialTheme.typography.bodyLarge, color = LegalDocumentTextColor)
                        TextButton(onClick = { viewModel.load(document) }) {
                            Text("Повторить")
                        }
                    }
                }
                uiState.fields.isNotEmpty() -> {
                    RequisitesContent(fields = uiState.fields)
                }
                !uiState.body.isNullOrBlank() -> {
                    TextDocumentContent(body = uiState.body!!)
                }
                else -> {
                    Text(
                        "Документ пуст",
                        modifier = Modifier.align(Alignment.Center),
                        color = LegalDocumentMutedColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextDocumentContent(body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = LegalDocumentTextColor,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )
    }
}

@Composable
private fun RequisitesContent(fields: List<LegalDocumentField>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Индивидуальный предприниматель Мацкова Александра Сергеевна",
                style = MaterialTheme.typography.bodyMedium,
                color = LegalDocumentMutedColor,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(fields) { field ->
            RequisiteRow(field = field)
        }
    }
}

@Composable
private fun RequisiteRow(field: LegalDocumentField) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LegalDocumentCardBackground,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = LegalDocumentMutedColor,
            )
            Text(
                text = field.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = LegalDocumentTextColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
