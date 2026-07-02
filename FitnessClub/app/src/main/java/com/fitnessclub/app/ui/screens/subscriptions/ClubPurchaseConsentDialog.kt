package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

data class ClubLegalLinks(
    val clubName: String,
    val offerUrl: String,
    val privacyUrl: String,
    val visitingRulesUrl: String? = null,
    val safetyRulesUrl: String? = null,
)

@Composable
fun ClubPurchaseConsentDialog(
    legalLinks: ClubLegalLinks,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подтвердите согласие перед покупкой") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Я подтверждаю, что перед покупкой абонемента в клубе «${legalLinks.clubName}» ознакомился(ась) с условиями документов:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ConsentLinkText(
                    text = "Договор (оферта) клуба",
                    url = legalLinks.offerUrl,
                    onOpen = { uriHandler.openUri(it) },
                )
                ConsentLinkText(
                    text = "Политика обработки персональных данных клуба",
                    url = legalLinks.privacyUrl,
                    onOpen = { uriHandler.openUri(it) },
                )
                legalLinks.visitingRulesUrl?.takeIf { it.isNotBlank() }?.let {
                    ConsentLinkText(
                        text = "Правила посещения клуба",
                        url = it,
                        onOpen = { uriHandler.openUri(it) },
                    )
                }
                legalLinks.safetyRulesUrl?.takeIf { it.isNotBlank() }?.let {
                    ConsentLinkText(
                        text = "Правила техники безопасности",
                        url = it,
                        onOpen = { uriHandler.openUri(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Согласен, приобрести абонемент")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun ConsentLinkText(
    text: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    val annotated = buildAnnotatedString {
        pushStringAnnotation("URL", url)
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
            append(text)
        }
        pop()
    }
    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
        modifier = Modifier.padding(start = 4.dp),
        onClick = { offset ->
            val annotations = annotated.getStringAnnotations("URL", offset, offset)
            annotations.firstOrNull()?.let { onOpen(it.item) }
        }
    )
}
