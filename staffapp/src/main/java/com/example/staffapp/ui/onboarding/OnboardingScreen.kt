package com.example.staffapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSecondaryButton
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

data class OnboardingUiState(
    val status: String = "pending_approval",
    val offerUrl: String = "https://dobrozal.ru/doc/offer",
    val amountRub: Double = 0.0,
    val rentalPaidUntil: String? = null,
    val offerAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onOfferAcceptedChange: (Boolean) -> Unit,
    onOpenOffer: () -> Unit,
    onPayClick: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = StaffPrimary) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Доброзал",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = when (state.status) {
                    "pending_approval" -> "Ожидание одобрения"
                    "needs_offer_payment" -> "Оплата аренды клуба"
                    "rejected" -> "Регистрация отклонена"
                    else -> "Доступ"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.status) {
                        "pending_approval" -> {
                            Text(
                                "Заявка отправлена администратору CRM. После одобрения вы сможете оплатить аренду и начать работу.",
                                color = StaffOnSurfaceVariant,
                            )
                            StaffPrimaryButton(
                                text = if (state.isLoading) "Проверяем..." else "Обновить статус",
                                onClick = onRefresh,
                                enabled = !state.isLoading,
                            )
                        }
                        "rejected" -> {
                            Text(
                                "Администратор отклонил регистрацию. Обратитесь в клуб.",
                                color = StaffOnSurfaceVariant,
                            )
                        }
                        "needs_offer_payment" -> {
                            Text(
                                "Ежемесячная аренда клуба: ${"%.0f".format(state.amountRub)} ₽",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Без оплаты доступ к рабочим разделам закрыт.",
                                color = StaffOnSurfaceVariant,
                            )
                            TextButton(onClick = onOpenOffer) {
                                Text("Открыть публичную оферту Доброзал", color = StaffPrimary)
                            }
                            RowAccept(
                                accepted = state.offerAccepted,
                                onChange = onOfferAcceptedChange,
                            )
                            StaffPrimaryButton(
                                text = if (state.isLoading) "Создаём платёж..." else "Оплатить аренду",
                                onClick = onPayClick,
                                enabled = !state.isLoading && state.offerAccepted,
                            )
                            StaffSecondaryButton(
                                text = "Проверить оплату",
                                onClick = onRefresh,
                                enabled = !state.isLoading,
                            )
                        }
                        else -> {
                            Text("Доступ открыт.", color = StaffOnSurfaceVariant)
                            StaffPrimaryButton(text = "Продолжить", onClick = onRefresh)
                        }
                    }
                    state.statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = StaffOnSurfaceVariant)
                    }
                    state.errorMessage?.let { StaffErrorState(message = it) }
                    StaffSecondaryButton(text = "Выйти", onClick = onLogout, enabled = !state.isLoading)
                }
            }
        }
    }
}

@Composable
private fun RowAccept(accepted: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = accepted, onCheckedChange = onChange)
        Text(
            "Принимаю публичную оферту Доброзал",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
