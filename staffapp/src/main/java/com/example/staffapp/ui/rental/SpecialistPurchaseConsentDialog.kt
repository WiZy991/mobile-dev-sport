package com.example.staffapp.ui.rental

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.staffapp.legal.StaffLegalPdf
import com.example.staffapp.ui.theme.StaffPrimary

@Composable
fun SpecialistPurchaseConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onOpenPdf: (StaffLegalPdf) -> Unit,
    isLoading: Boolean = false,
) {
    var safetyBriefed by remember { mutableStateOf(false) }
    var sessionRules by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "Подтвердите согласие перед покупкой",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Вы приобретаете профессиональный доступ в Клуб.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Нажимая «Приобрести абонемент», вы подтверждаете, что ознакомились с тарифом и условиями доступа, а также со следующими документами:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Оферта для специалистов",
                        color = StaffPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPdf(StaffLegalPdf.PRO_OFFER) }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = "Политика обработки и защиты персональных данных Клуба",
                        color = StaffPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = TextDecoration.Underline,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPdf(StaffLegalPdf.DOBROZAL_PRIVACY) }
                            .padding(vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Switch(
                            checked = safetyBriefed,
                            onCheckedChange = { safetyBriefed = it },
                            enabled = !isLoading,
                        )
                        Text(
                            text = "Я проинструктирован по технике безопасности в Клубе",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Switch(
                            checked = sessionRules,
                            onCheckedChange = { sessionRules = it },
                            enabled = !isLoading,
                        )
                        Text(
                            text = "Я ознакомлен с правилами проведения сессий с клиентами",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = safetyBriefed && sessionRules && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StaffPrimary),
                    ) {
                        Text(
                            text = if (isLoading) "Оформляем…" else "Приобрести абонемент",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отмена", color = StaffPrimary)
                    }
                }
            }
        }
    }
}
