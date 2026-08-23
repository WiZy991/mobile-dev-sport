package com.example.staffapp.ui.rental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.RentalPaymentItem
import com.example.staffapp.RentalPlan
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffLoadingState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSectionTitle
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.work.ListCardUi

data class RentalScreenState(
    val rentalPaidUntilLabel: String? = null,
    val plans: List<RentalPlan> = emptyList(),
    val selectedMonths: Int = 1,
    val offerAccepted: Boolean = true,
    val payments: List<RentalPaymentItem> = emptyList(),
    val loading: Boolean = true,
    val paying: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalScreen(
    state: RentalScreenState,
    onBack: () -> Unit,
    onPlanSelected: (Int) -> Unit,
    onPayClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val selected = state.plans.firstOrNull { it.months == state.selectedMonths }
        ?: state.plans.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аренда клуба") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.rentalPaidUntilLabel?.let {
                item { StaffInfoBanner(it) }
            }
            item { StaffSectionTitle("Продлить") }
            if (state.plans.isNotEmpty()) {
                item {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.plans.forEach { plan ->
                            FilterChip(
                                selected = plan.months == state.selectedMonths,
                                onClick = { onPlanSelected(plan.months) },
                                label = { Text("${plan.label}: ${"%.0f".format(plan.amountRub)} ₽") },
                            )
                        }
                    }
                }
            }
            item {
                StaffPrimaryButton(
                    text = when {
                        state.paying -> "Создаём платёж..."
                        selected != null -> "Оплатить ${"%.0f".format(selected.amountRub)} ₽"
                        else -> "Оплатить аренду"
                    },
                    onClick = onPayClick,
                    enabled = !state.paying && !state.loading && selected != null,
                )
            }
            state.statusMessage?.let {
                item { Text(it, color = StaffOnSurfaceVariant) }
            }
            state.errorMessage?.let { item { StaffErrorState(message = it, onRetry = onRefresh) } }
            item { StaffSectionTitle("История платежей") }
            if (state.loading) {
                item { StaffLoadingState() }
            } else if (state.payments.isEmpty()) {
                item {
                    Text(
                        "История платежей пока пуста (или API ещё не задеплоен на сервере).",
                        color = StaffOnSurfaceVariant,
                    )
                }
            } else {
                items(state.payments, key = { it.id }) { payment ->
                    StaffListCard(
                        item = ListCardUi(
                            title = "${"%.0f".format(payment.amountRub)} ₽ · ${payment.durationMonths} мес.",
                            subtitle = statusLabel(payment.status),
                            meta = payment.paidAt ?: payment.createdAt ?: "",
                        ),
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "paid" -> "Оплачено"
    "pending" -> "Ожидает оплаты"
    "failed" -> "Ошибка"
    "expired" -> "Истёк"
    "cancelled" -> "Отменён"
    else -> status
}
