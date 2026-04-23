package com.fitnessclub.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterPassportScreen(
    viewModel: RegisterViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val p = uiState.passport
    var issuePickerOpen by remember { mutableStateOf(false) }

    if (issuePickerOpen) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { issuePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { viewModel.setPassportIssuedDateFromMillis(it) }
                    issuePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { issuePickerOpen = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Мой паспорт", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            uiState.passportError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    TextField(
                        value = p.series,
                        onValueChange = viewModel::onPassportSeriesChange,
                        label = { Text("Серия паспорта") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = lightFieldColors()
                    )
                    Text("${p.series.length}/5", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    TextField(
                        value = p.number,
                        onValueChange = viewModel::onPassportNumberChange,
                        label = { Text("Номер паспорта") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = lightFieldColors()
                    )
                    Text("${p.number.length}/7", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(12.dp))
            TextField(
                value = p.issuedBy,
                onValueChange = viewModel::onPassportIssuedByChange,
                label = { Text("Кем выдан") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = lightFieldColors()
            )
            Text("${p.issuedBy.length}/300", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                TextField(
                    value = p.issuedDateDisplay,
                    onValueChange = viewModel::onPassportIssuedDateChange,
                    label = { Text("Выдан") },
                    placeholder = { Text("дд.мм.гггг") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = lightFieldColors()
                )
                IconButton(onClick = { issuePickerOpen = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Календарь")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Адрес прописки",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TextField(
                value = p.region,
                onValueChange = viewModel::onPassportRegionChange,
                label = { Text("Регион/область") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = lightFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = p.city,
                onValueChange = viewModel::onPassportCityChange,
                label = { Text("Город") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = lightFieldColors()
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = p.streetHouse,
                onValueChange = viewModel::onPassportStreetChange,
                label = { Text("Улица и дом") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = lightFieldColors()
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (viewModel.validatePassportDraft()) onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
            ) {
                Text("СОХРАНИТЬ", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun lightFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
)
