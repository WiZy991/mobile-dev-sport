package com.example.staffapp.ui.rental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.staffapp.RentalClubOption
import com.example.staffapp.RentalPaymentItem
import com.example.staffapp.ui.components.RentalClubSelectCard
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
    val clubs: List<RentalClubOption> = emptyList(),
    val selectedClubId: Int? = null,
    val rentalDays: Int = 30,
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
    onClubSelected: (Int) -> Unit,
    onPayClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val selected = state.clubs.firstOrNull { it.clubId == state.selectedClubId }
        ?: state.clubs.firstOrNull()

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
            item { StaffSectionTitle("Выберите зал · ${state.rentalDays} дней") }
            if (state.clubs.isEmpty() && !state.loading) {
                item {
                    Text(
                        "Каталог залов пока пуст. Добавьте клуб в CRM (раздел клубов) и обновите экран.",
                        color = StaffOnSurfaceVariant,
                    )
                }
            } else {
                items(state.clubs, key = { it.clubId }) { club ->
                    RentalClubSelectCard(
                        club = club,
                        selected = club.clubId == selected?.clubId,
                        onClick = { onClubSelected(club.clubId) },
                        showPaidStatus = true,
                    )
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
                        "История платежей пока пуста.",
                        color = StaffOnSurfaceVariant,
                    )
                }
            } else {
                items(state.payments, key = { it.id }) { payment ->
                    val clubPart = payment.clubName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    StaffListCard(
                        item = ListCardUi(
                            title = "${"%.0f".format(payment.amountRub)} ₽ · ${state.rentalDays} дн.$clubPart",
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
