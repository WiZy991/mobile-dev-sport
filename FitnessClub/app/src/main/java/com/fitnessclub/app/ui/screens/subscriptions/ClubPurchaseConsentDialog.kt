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
                    text = "Перед приобретением абонемента ознакомьтесь с условиями оказания услуг клуба «${legalLinks.clubName}».",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Нажимая кнопку «Согласен, приобрести абонемент», я подтверждаю, что ознакомился(ась) и соглашаюсь с условиями публичной оферты клуба, правилами посещения, условиями выбранного абонемента, его стоимостью, сроком действия, порядком использования и ограничениями.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Я понимаю и соглашаюсь, что приобретение абонемента означает акцепт публичной оферты клуба, а договор заключается непосредственно между мной и клубом.",
                    style = MaterialTheme.typography.bodySmall,
                )
                ConsentLinkText(
                    text = "С условиями публичной оферты можно ознакомиться по ссылке",
                    url = legalLinks.offerUrl,
                    onOpen = { uriHandler.openUri(it) },
                )
                ConsentLinkText(
                    text = "С Политикой по обработке персональных данных можно ознакомиться по ссылке",
                    url = legalLinks.privacyUrl,
                    onOpen = { uriHandler.openUri(it) },
                )
                legalLinks.visitingRulesUrl?.takeIf { it.isNotBlank() }?.let {
                    ConsentLinkText(
                        text = "С правилами посещения клуба можно ознакомиться по ссылке",
                        url = it,
                        onOpen = { uriHandler.openUri(it) },
                    )
                }
                legalLinks.safetyRulesUrl?.takeIf { it.isNotBlank() }?.let {
                    ConsentLinkText(
                        text = "С техникой безопасности клуба можно ознакомиться по ссылке",
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
