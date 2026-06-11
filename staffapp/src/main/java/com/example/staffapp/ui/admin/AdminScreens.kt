package com.example.staffapp.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffActionButtons
import com.example.staffapp.ui.components.StaffEmptyState
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffHeroCard
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffLoadingState
import com.example.staffapp.ui.components.StaffMenuCard
import com.example.staffapp.ui.components.StaffMetricsRow
import com.example.staffapp.ui.components.StaffSectionTitle
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary
import com.example.staffapp.ui.work.ActionUi
import com.example.staffapp.ui.work.ListCardUi
import com.example.staffapp.ui.work.MetricUi
import com.example.staffapp.ui.work.SectionIcons

data class AdminHubUi(
    val canWrite: Boolean = false,
    val metrics: List<MetricUi> = emptyList(),
    val sections: List<AdminSectionRowUi> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class AdminSectionRowUi(
    val key: String,
    val title: String,
    val hint: String,
)

data class AdminSectionUi(
    val title: String = "",
    val metrics: List<MetricUi> = emptyList(),
    val items: List<ListCardUi> = emptyList(),
    val shortcuts: List<ActionUi> = emptyList(),
    val summary: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHubScreen(
    state: AdminHubUi,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSectionClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Админка", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                    }
                },
                colors = topBarColors(),
            )
        },
    ) { padding ->
        when {
            state.loading -> StaffLoadingState("Загрузка админки...")
            state.error != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                StaffErrorState(state.error, onRetry)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StaffHeroCard(
                        title = "CRM",
                        subtitle = if (state.canWrite) "Редактирование разрешено" else "Только просмотр",
                    )
                }
                if (state.metrics.isNotEmpty()) {
                    item { StaffMetricsRow(state.metrics) }
                }
                item { StaffSectionTitle("Разделы") }
                if (state.sections.isEmpty()) {
                    item { StaffEmptyState("Нет доступных разделов") }
                } else {
                    item {
                        StaffMenuCard(
                            title = "Откройте раздел",
                            items = state.sections.map { s ->
                                SectionIcons.forSection(s.key) to (s.title to s.hint)
                            },
                            onItemClick = { index -> onSectionClick(state.sections[index].key) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSectionScreen(
    state: AdminSectionUi,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    onItemClick: (ListCardUi) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = topBarColors(),
            )
        },
    ) { padding ->
        when {
            state.loading -> BoxedLoading(padding)
            state.error != null -> Column(Modifier.padding(padding).padding(16.dp)) {
                StaffErrorState(state.error, onRetry)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.summary.isNotBlank()) {
                    item { StaffInfoBanner(state.summary) }
                }
                if (state.metrics.isNotEmpty()) {
                    item { StaffMetricsRow(state.metrics) }
                }
                if (state.shortcuts.isNotEmpty()) {
                    item { StaffActionButtons(state.shortcuts, onAction) }
                }
                item { StaffSectionTitle("Записи") }
                if (state.items.isEmpty()) {
                    item { StaffEmptyState("В этом разделе пока пусто") }
                } else {
                    items(state.items) { item ->
                        StaffListCard(
                            item = item,
                            onClick = if (item.isClickable) {{ onItemClick(item) }} else null,
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun BoxedLoading(padding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Center,
    ) {
        StaffLoadingState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun topBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = StaffPrimary,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)
