package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.data.model.SubscriptionType
import com.fitnessclub.app.ui.theme.Error
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SubscriptionPurchaseConfirmDialog(
    plan: SubscriptionPlan,
    finalPrice: Double,
    hasDiscount: Boolean,
    listPrice: Double? = null,
    isLoading: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onOpenRequisites: () -> Unit,
    onConfirm: () -> Unit,
    paymentAvailable: Boolean = Brand.onlinePaymentEnabled,
) {
    var showDetails by remember { mutableStateOf(false) }
    val details = plan.details?.trim().orEmpty()
    val description = plan.safeDescription.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтверждение покупки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = plan.safeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                plan.clubName?.takeIf { it.isNotBlank() }?.let {
                    PlanFact("Клуб", it)
                }
                val today = LocalDate.now()
                val start = plan.startsOn
                    ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                    ?.takeIf { it.isAfter(today) }
                val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                if (plan.safeDurationDays > 0) {
                    val until = (start ?: today).plusDays(plan.safeDurationDays.toLong()).format(dateFmt)
                    PlanFact(
                        "Срок",
                        if (start != null) {
                            "${plan.safeDurationDays} дн. — с ${start.format(dateFmt)} по $until"
                        } else {
                            "${plan.safeDurationDays} дн. — при покупке сегодня до $until"
                        },
                    )
                }
                if (start != null) {
                    Text(
                        text = "У вас есть действующий абонемент — новый начнёт действовать после его окончания.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                PlanFact(
                    "Посещения",
                    plan.visitsCount?.let { "$it" }
                        ?: if (plan.type == SubscriptionType.UNLIMITED || plan.type == null) "без ограничений" else "—",
                )
                plan.freezeDaysTotal?.let {
                    PlanFact("Заморозка", if (it > 0) "до $it дн." else "не предусмотрена")
                }
                if (description.isNotEmpty() || details.isNotEmpty() || plan.safeFeatures.isNotEmpty()) {
                    TextButton(
                        onClick = { showDetails = !showDetails },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(if (showDetails) "Скрыть условия" else "Подробнее об условиях")
                        Icon(
                            if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    AnimatedVisibility(visible = showDetails) {
                        Column {
                            if (description.isNotEmpty()) {
                                Text(description, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                            }
                            plan.safeFeatures.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (hasDiscount && listPrice != null) {
                    Text(
                        text = "Было: ${listPrice.toInt()} ₽",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Стоимость: ${finalPrice.toInt()} ₽",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (paymentAvailable) {
                        "Оплата производится на защищённой странице банка."
                    } else {
                        "Онлайн-оплата скоро появится в приложении. Оформить абонемент можно у администратора клуба."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Brand.legalEntityLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Brand.legalEntityLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onOpenRequisites,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "Реквизиты",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (paymentAvailable) {
                Button(onClick = onConfirm, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Далее")
                    }
                }
            } else {
                Button(onClick = onDismiss) { Text("Понятно") }
            }
        },
        dismissButton = if (paymentAvailable) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun PlanFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
