package com.example.staffapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.staffapp.ui.theme.StaffAccentBlue
import com.example.staffapp.ui.theme.StaffError
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary
import com.example.staffapp.ui.theme.StaffSuccess
import com.example.staffapp.ui.theme.StaffWarning
import com.example.staffapp.ui.work.ActionUi
import com.example.staffapp.ui.work.BadgeColor
import com.example.staffapp.ui.work.DayChipUi
import com.example.staffapp.ui.work.ListCardUi
import com.example.staffapp.ui.work.MetricUi

@Composable
fun StaffScreenContainer(
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { content() }
    }
}

@Composable
fun StaffSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun StaffHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StaffPrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
fun StaffMetricsRow(metrics: List<MetricUi>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(metrics) { metric ->
            Card(
                modifier = Modifier.width(140.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = StaffPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = StaffOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun StaffChipRow(
    chips: List<DayChipUi>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(chips) { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = { onChipClick(chip.date) },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(chip.label, style = MaterialTheme.typography.labelMedium)
                        if (chip.count >= 0) {
                            Text(
                                "${chip.count}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StaffPrimary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffListCard(
    item: ListCardUi,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clickable = onClick != null
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val cardShape = RoundedCornerShape(16.dp)
    val cardBody: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StaffBadge(text = item.badge, color = item.badgeColor)
                    }
                }
                if (item.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (item.meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = StaffOnSurfaceVariant,
                    )
                }
            }
            if (clickable) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = StaffOnSurfaceVariant,
                )
            }
        }
    }
    if (clickable) {
        Card(
            onClick = onClick!!,
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
            content = cardBody,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
            content = cardBody,
        )
    }
}

@Composable
fun StaffBadge(text: String, color: BadgeColor) {
    val bg = when (color) {
        BadgeColor.SUCCESS -> StaffSuccess.copy(alpha = 0.15f)
        BadgeColor.WARNING -> StaffWarning.copy(alpha = 0.2f)
        BadgeColor.ERROR -> StaffError.copy(alpha = 0.15f)
        BadgeColor.PRIMARY -> StaffPrimary.copy(alpha = 0.12f)
        BadgeColor.NEUTRAL -> StaffOnSurfaceVariant.copy(alpha = 0.12f)
    }
    val fg = when (color) {
        BadgeColor.SUCCESS -> StaffSuccess
        BadgeColor.WARNING -> StaffWarning
        BadgeColor.ERROR -> StaffError
        BadgeColor.PRIMARY -> StaffPrimary
        BadgeColor.NEUTRAL -> StaffOnSurfaceVariant
    }
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun StaffMenuCard(
    title: String,
    items: List<Pair<ImageVector, Pair<String, String>>>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            items.forEachIndexed { index, (icon, texts) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(index) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StaffPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = StaffPrimary)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(texts.first, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (texts.second.isNotBlank()) {
                            Text(
                                texts.second,
                                style = MaterialTheme.typography.bodySmall,
                                color = StaffOnSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = StaffOnSurfaceVariant,
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun StaffSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = StaffPrimary.copy(alpha = 0.12f),
            contentColor = StaffPrimary,
        ),
    ) {
        Text(text)
    }
}

@Composable
fun StaffPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StaffPrimary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
    ) {
        Text(text)
    }
}

@Composable
fun StaffActionButtons(
    actions: List<ActionUi>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            if (action.id.startsWith("text:")) {
                TextButton(onClick = { onAction(action.id) }) {
                    Text(action.label, color = StaffPrimary)
                }
            } else if (action.id.startsWith("secondary:")) {
                StaffSecondaryButton(text = action.label, onClick = { onAction(action.id) })
            } else {
                StaffPrimaryButton(text = action.label, onClick = { onAction(action.id) })
            }
        }
    }
}

@Composable
fun StaffSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Имя, email или телефон") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSearch,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StaffPrimary),
            ) {
                Text("Найти")
            }
        }
    }
}

@Composable
fun StaffLoadingState(message: String = "Загрузка...") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = StaffPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = StaffOnSurfaceVariant)
        }
    }
}

@Composable
fun StaffEmptyState(
    message: String,
    icon: ImageVector = Icons.Default.Inbox,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = StaffOnSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = StaffOnSurfaceVariant)
        }
    }
}

@Composable
fun StaffErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StaffError.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StaffError)
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, color = StaffError, style = MaterialTheme.typography.bodyMedium)
            }
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onRetry) { Text("Повторить", color = StaffPrimary) }
            }
        }
    }
}

@Composable
fun StaffInfoBanner(text: String, color: Color = StaffAccentBlue) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
