package com.fitnessclub.app.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessclub.app.data.config.ClubPurchaseDocuments
import com.fitnessclub.app.data.config.LegalPdfAsset
import com.fitnessclub.app.ui.theme.Primary

data class ClubPurchaseContext(
    val clubName: String,
    val visitingRulesUrl: String? = null,
    val safetyRulesUrl: String? = null,
)

@Composable
fun ClubPurchaseConsentDialog(
    context: ClubPurchaseContext,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onOpenPdf: (LegalPdfAsset) -> Unit,
    onOpenExternalUrl: (String) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                        text = "Вы покупаете абонемент у клуба «${context.clubName}».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Нажимая «Согласен, приобрести абонемент», вы подтверждаете, что ознакомились с публичной офертой, политикой обработки персональных данных, согласием на обработку персональных данных, правилами посещения, стоимостью, сроком действия и условиями абонемента.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Договор заключается напрямую между вами и клубом.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ConsentLinkText(
                        text = "публичная оферта",
                        onClick = { onOpenPdf(ClubPurchaseDocuments.offer) },
                    )
                    ConsentLinkText(
                        text = "политика обработки персональных данных",
                        onClick = { onOpenPdf(ClubPurchaseDocuments.privacy) },
                    )
                    ConsentLinkText(
                        text = "согласие на обработку персональных данных",
                        onClick = { onOpenPdf(ClubPurchaseDocuments.consent) },
                    )
                    context.visitingRulesUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        ConsentLinkText(
                            text = "правила посещения",
                            onClick = { onOpenExternalUrl(url) },
                        )
                    }
                    context.safetyRulesUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        ConsentLinkText(
                            text = "техника безопасности",
                            onClick = { onOpenExternalUrl(url) },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(
                            text = "Согласен, приобрести абонемент",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentLinkText(
    text: String,
    onClick: () -> Unit,
) {
    val annotated = buildAnnotatedString {
        pushStringAnnotation("LINK", "link")
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
            val annotations = annotated.getStringAnnotations("LINK", offset, offset)
            if (annotations.isNotEmpty()) onClick()
        },
    )
}
