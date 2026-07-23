package com.example.staffapp.ui.client

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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffEmptyState
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffHeroCard
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffLoadingState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSectionTitle
import com.example.staffapp.ui.theme.StaffError
import com.example.staffapp.ui.theme.StaffPrimary
import com.example.staffapp.ui.work.BadgeColor
import com.example.staffapp.ui.work.ListCardUi

data class ClientDetailUi(
    val title: String = "Клиент",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val bonusPoints: Int = 0,
    val isBlocked: Boolean = false,
    val subscriptionTitle: String = "",
    val subscriptionMeta: String = "",
    val bookings: List<ListCardUi> = emptyList(),
    val tickets: List<ListCardUi> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val showCallButton: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    state: ClientDetailUi,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клиент", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StaffPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                StaffLoadingState("Загрузка карточки...")
            }
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
                        title = state.name,
                        subtitle = listOf(state.email, state.phone).filter { it.isNotBlank() }.joinToString("\n"),
                    )
                }
                if (state.isBlocked) {
                    item {
                        StaffInfoBanner("Клиент заблокирован", color = StaffError)
                    }
                }
                item {
                    StaffListCard(
                        ListCardUi(
                            title = "Бонусы",
                            subtitle = "${state.bonusPoints} баллов",
                        ),
                    )
                }
                item { StaffSectionTitle("Абонемент") }
                item {
                    StaffListCard(
                        ListCardUi(
                            title = state.subscriptionTitle.ifBlank { "Нет активного абонемента" },
                            subtitle = state.subscriptionMeta,
                            badge = "Абонемент",
                            badgeColor = BadgeColor.PRIMARY,
                        ),
                    )
                }
                if (state.showCallButton) {
                    item {
                        StaffPrimaryButton(text = "Позвонить", onClick = onCall)
                    }
                }
                item { StaffSectionTitle("Последние записи") }
                if (state.bookings.isEmpty()) {
                    item { StaffEmptyState("Нет записей", icon = Icons.Default.Event) }
                } else {
                    items(state.bookings) { StaffListCard(it) }
                }
                item { StaffSectionTitle("Обращения") }
                if (state.tickets.isEmpty()) {
                    item { StaffEmptyState("Нет обращений", icon = Icons.Default.SupportAgent) }
                } else {
                    items(state.tickets) { StaffListCard(it) }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
