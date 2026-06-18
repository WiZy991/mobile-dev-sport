package com.fitnessclub.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.data.api.ClubItem
import com.fitnessclub.app.data.catalog.LocalSubscriptionCatalog
import com.fitnessclub.app.data.model.SubscriptionPlan
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.PrimaryVariant
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RegisterClubPickScreen(
    selectedClubId: String?,
    onBack: () -> Unit,
    onPicked: (ClubItem) -> Unit,
    onRequestSberRegistration: (String) -> Unit = {},
) {
    val scroll = rememberScrollState()
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var showSberDialog by remember { mutableStateOf(false) }

    fun toggleExpand(clubId: String) {
        expandedIds = if (expandedIds.contains(clubId)) {
            expandedIds - clubId
        } else {
            expandedIds + clubId
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Primary, PrimaryVariant))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = "Выберите зал",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Клуб привяжется к вашему аккаунту",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
            )

            RegistrationVenues.orderedCards.forEach { card ->
                val selected = card.clubId == selectedClubId
                val expanded = expandedIds.contains(card.clubId)
                val pickImageInteraction = remember(card.clubId) { MutableInteractionSource() }
                val pickTitleInteraction = remember("${card.clubId}_title") { MutableInteractionSource() }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .then(
                            if (selected) {
                                Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            },
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.12f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Image(
                            painter = painterResource(card.imageRes),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = pickImageInteraction,
                                    indication = null,
                                ) { onPicked(RegistrationVenues.toClubItem(card)) },
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = pickTitleInteraction,
                                        indication = null,
                                    ) { onPicked(RegistrationVenues.toClubItem(card)) },
                            ) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = card.addressLines,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(0.88f),
                                )
                            }
                            IconButton(
                                onClick = { toggleExpand(card.clubId) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Свернуть цены" else "Показать цены",
                                    tint = Color.White,
                                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                HorizontalDivider(
                                    color = Color.White.copy(0.25f),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                                Text(
                                    text = "Абонементы",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                LocalSubscriptionCatalog.PLANS_PRICELIST_ORDER.forEachIndexed { index, plan ->
                                    if (index > 0) {
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    PriceListRow(plan = plan)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { showSberDialog = true },
                enabled = selectedClubId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Выбрать зал")
            }
        }

        if (showSberDialog) {
            AlertDialog(
                onDismissRequest = { showSberDialog = false },
                title = { Text("Зарегистрироваться с помощью Сбер ID") },
                text = {
                    Text(
                        "Продолжим через Сбер ID. Откроется защищённый вход, после успешной авторизации вы вернётесь в приложение.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clubId = selectedClubId
                        showSberDialog = false
                        if (clubId != null) {
                            onRequestSberRegistration(clubId)
                        }
                    }) {
                        Text("Войти через Сбер ID")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSberDialog = false }) {
                        Text("Позже")
                    }
                },
            )
        }
    }
}

@Composable
private fun PriceListRow(plan: SubscriptionPlan) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = plan.safeName,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Цена: ${formatPriceRu(plan.price)} ₽",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(0.95f),
        )
        LocalSubscriptionCatalog.freezeSubtitleForPlan(plan.safeId)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.8f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatPriceRu(value: Double): String {
    val n = kotlin.math.abs(value.toInt())
    return NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(n)
}
