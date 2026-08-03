package com.fitnessclub.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitnessclub.app.ui.theme.AccentBlue
import com.fitnessclub.app.ui.theme.AppShapes
import com.fitnessclub.app.ui.theme.AccentOrange
import com.fitnessclub.app.ui.theme.Primary

private const val CLUB_NAME_MAX_CHARS = 36

@Composable
fun OccupancyCard(
    current: Int?,
    max: Int?,
    percentage: Int?,
    status: String?,
    clubName: String? = null,
    onRefresh: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val progressColor = when (status) {
        "low" -> AccentBlue
        "high" -> AccentOrange
        else -> Primary
    }
    val strokeWidthPx = with(LocalDensity.current) { 8.dp.toPx() }
    val displayName = clubName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { truncateClubName(it, CLUB_NAME_MAX_CHARS) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        shape = AppShapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                "low" -> AccentBlue.copy(alpha = 0.15f)
                "high" -> AccentOrange.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sweep = (percentage?.toFloat() ?: 0f) / 100f * 360f
                    drawArc(
                        color = outlineColor,
                        startAngle = 270f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                        size = Size(size.minDimension - strokeWidthPx, size.minDimension - strokeWidthPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = 270f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                        size = Size(size.minDimension - strokeWidthPx, size.minDimension - strokeWidthPx),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (displayName != null) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "Заполненность зала",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                if (current != null && max != null) {
                    Text(
                        text = "$current из $max человек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Загрузка...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Открыть клуб",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun truncateClubName(name: String, maxChars: Int): String {
    if (name.length <= maxChars) return name
    return name.take(maxChars - 1).trimEnd() + "…"
}
