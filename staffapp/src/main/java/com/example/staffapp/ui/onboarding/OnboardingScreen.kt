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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.RentalClubOption
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSecondaryButton
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

data class OnboardingUiState(
    val status: String = "pending_approval",
    val amountRub: Double = 0.0,
    val rentalClubs: List<RentalClubOption> = emptyList(),
    val selectedClubId: Int? = null,
    val rentalDays: Int = 30,
    val rentalPaidUntil: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onClubSelected: (Int) -> Unit,
    onPayClick: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    val selectedClub = state.rentalClubs.firstOrNull { it.clubId == state.selectedClubId }
        ?: state.rentalClubs.firstOrNull()
    val payAmount = selectedClub?.amountRub ?: state.amountRub

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
                    "needs_offer_payment" -> "Оплата доступа специалиста"
                    "needs_profile" -> "Заполнение профиля"
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
                                "Заявка отправлена администратору CRM. После одобрения вы сможете оплатить доступ и начать работу.",
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
                                "Выберите зал · ${state.rentalDays} дней",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.rentalClubs.isNotEmpty()) {
                                state.rentalClubs.forEach { club ->
                                    FilterChip(
                                        selected = club.clubId == selectedClub?.clubId,
                                        onClick = { onClubSelected(club.clubId) },
                                        label = {
                                            Text("${club.title}\n${"%.0f".format(club.amountRub)} ₽")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                Text(
                                    "Доступ в клуб: ${"%.0f".format(state.amountRub)} ₽ / ${state.rentalDays} дн.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "К оплате: ${"%.0f".format(payAmount)} ₽",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Без оплаты доступ к рабочим разделам закрыт. Перед оплатой нужно подтвердить оферту и инструктаж.",
                                color = StaffOnSurfaceVariant,
                            )
                            StaffPrimaryButton(
                                text = if (state.isLoading) "Создаём платёж..." else "Приобрести абонемент",
                                onClick = onPayClick,
                                enabled = !state.isLoading && (selectedClub != null || state.rentalClubs.isEmpty()),
                            )
                            StaffSecondaryButton(
                                text = "Проверить оплату",
                                onClick = onRefresh,
                                enabled = !state.isLoading,
                            )
                        }
                        "needs_profile" -> {
                            Text(
                                "Осталось заполнить карточку специалиста: телефон и специализацию. " +
                                    "Без этого клиенты не увидят вас в приложении.",
                                color = StaffOnSurfaceVariant,
                            )
                            StaffPrimaryButton(
                                text = if (state.isLoading) "Открываем..." else "Заполнить профиль",
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
