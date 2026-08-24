package com.example.staffapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.staffapp.RentalClubOption
import com.example.staffapp.ui.theme.StaffOnSurface
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary
import com.example.staffapp.ui.theme.StaffSuccess

private val ClubCardShape = RoundedCornerShape(16.dp)
private val SelectedFill = Color(0xFFFFF0EA)
private val UnselectedBorder = Color(0xFFE8E4E1)

@Composable
fun RentalClubSelectCard(
    club: RentalClubOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPaidStatus: Boolean = false,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) StaffPrimary else UnselectedBorder,
        animationSpec = tween(180),
        label = "clubBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) SelectedFill else Color.White,
        animationSpec = tween(180),
        label = "clubFill",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(ClubCardShape)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = ClubCardShape)
            .clickable(onClick = onClick),
        shape = ClubCardShape,
        color = containerColor,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (selected) StaffPrimary.copy(alpha = 0.15f) else Color(0xFFF3F1F0),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Place,
                    contentDescription = null,
                    tint = if (selected) StaffPrimary else StaffOnSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = club.name.ifBlank { "Зал" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StaffOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (club.address.isNotBlank()) {
                    Text(
                        text = club.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = StaffOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
                if (showPaidStatus) {
                    val status = when {
                        club.rentalActive && !club.paidUntil.isNullOrBlank() ->
                            "Оплачен до ${club.paidUntil.take(10)}"
                        club.rentalActive -> "Активен"
                        else -> "Не оплачен"
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (club.rentalActive) StaffSuccess else StaffOnSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.0f ₽".format(club.amountRub),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) StaffPrimary else StaffOnSurface,
                )
                Text(
                    text = "/ 30 дн.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StaffOnSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = if (selected) StaffPrimary else Color.Transparent,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (selected) StaffPrimary else UnselectedBorder,
                ),
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}
