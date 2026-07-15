package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ClubLegalDocumentsMissingDialog(
    clubName: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Документы клуба не настроены",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Перед покупкой абонемента клуб «$clubName» должен опубликовать свою публичную оферту и политику обработки персональных данных.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Сейчас в системе не указаны ссылки на документы владельца клуба (ИП). Обратитесь в клуб или администратору CRM — раздел «Настройки клуба» → «Документы для покупки абонемента».",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Понятно")
            }
        },
    )
}
