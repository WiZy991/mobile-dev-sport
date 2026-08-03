package com.example.staffapp.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

enum class ClientBookingTab { ACTIVE, COMPLETED }

data class ClientDetailUi(
    val title: String = "Клиент",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isBlocked: Boolean = false,
    val subscriptionTitle: String = "",
    val subscriptionMeta: String = "",
    val activeBookings: List<ListCardUi> = emptyList(),
    val completedBookings: List<ListCardUi> = emptyList(),
    val bookingTab: ClientBookingTab = ClientBookingTab.ACTIVE,
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
    onBookingTabSelected: (ClientBookingTab) -> Unit = {},
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
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                StaffLoadingState("Загрузка карточки...")
            }
            state.error != null -> Column(
                Modifier.padding(padding).padding(16.dp),
            ) {
                StaffErrorState(state.error, onRetry)
            }
            else -> {
                val bookings = when (state.bookingTab) {
                    ClientBookingTab.ACTIVE -> state.activeBookings
                    ClientBookingTab.COMPLETED -> state.completedBookings
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        StaffHeroCard(
                            title = state.name,
                            subtitle = listOf(state.email, state.phone)
                                .filter { it.isNotBlank() }
                                .joinToString("\n"),
                        )
                    }
                    if (state.isBlocked) {
                        item {
                            StaffInfoBanner("Клиент заблокирован", color = StaffError)
                        }
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
                    item { StaffSectionTitle("Записи") }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.bookingTab == ClientBookingTab.ACTIVE,
                                onClick = { onBookingTabSelected(ClientBookingTab.ACTIVE) },
                                label = {
                                    Text("Активные (${state.activeBookings.size})")
                                },
                            )
                            FilterChip(
                                selected = state.bookingTab == ClientBookingTab.COMPLETED,
                                onClick = { onBookingTabSelected(ClientBookingTab.COMPLETED) },
                                label = {
                                    Text("Завершённые (${state.completedBookings.size})")
                                },
                            )
                        }
                    }
                    if (bookings.isEmpty()) {
                        item {
                            StaffEmptyState(
                                if (state.bookingTab == ClientBookingTab.ACTIVE) {
                                    "Нет активных записей"
                                } else {
                                    "Нет завершённых записей"
                                },
                                icon = Icons.Default.Event,
                            )
                        }
                    } else {
                        items(bookings) { StaffListCard(it) }
                    }
                    item { StaffSectionTitle("Обращения") }
                    if (state.tickets.isEmpty()) {
                        item { StaffEmptyState("Нет обращений", icon = Icons.Default.SupportAgent) }
                    } else {
                        items(state.tickets) { StaffListCard(it) }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
