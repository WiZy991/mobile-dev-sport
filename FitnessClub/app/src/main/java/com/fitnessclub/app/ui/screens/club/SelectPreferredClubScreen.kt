package com.fitnessclub.app.ui.screens.club

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.R
import com.fitnessclub.app.data.api.ClubItem
import com.fitnessclub.app.ui.screens.auth.RegistrationVenues
import com.fitnessclub.app.ui.theme.Primary
import com.fitnessclub.app.ui.theme.PrimaryVariant

/**
 * Выбор «моего» зала: влияет на главную/профиль и на то, к какому залу
 * привяжется следующий купленный абонемент (проход только в этот зал).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPreferredClubScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit = onBack,
    title: String = "Выбрать клуб",
    subtitle: String = "Абонемент действует только в выбранном зале — в другие по нему не пройти.",
    viewModel: SelectPreferredClubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        containerColor = Color(0xFFF6F4F2),
    ) { padding ->
        when {
            uiState.isLoading && uiState.clubs.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            uiState.error != null && uiState.clubs.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.error ?: "Ошибка", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.22f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF3D342F),
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }
                    items(uiState.clubs, key = { it.id }) { club ->
                        val display = remember(club.id, club.name, club.address) {
                            clubDisplayFor(club)
                        }
                        ClubPickCard(
                            title = display.title,
                            address = display.address,
                            imageRes = display.imageRes,
                            selected = club.id == uiState.selectedClubId,
                            enabled = !uiState.isSaving,
                            onClick = {
                                viewModel.selectClub(club.id) { ok ->
                                    if (ok) onSelected()
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private data class ClubDisplay(
    val title: String,
    val address: String,
    @DrawableRes val imageRes: Int,
)

private fun clubDisplayFor(club: ClubItem): ClubDisplay {
    val venue = RegistrationVenues.cardByClubId(club.id)
        ?: RegistrationVenues.orderedCards.find {
            club.name.contains(it.title, ignoreCase = true) ||
                it.title.contains(club.name.substringBefore(',').trim(), ignoreCase = true)
        }
    if (venue != null) {
        return ClubDisplay(venue.title, venue.addressLines, venue.imageRes)
    }
    val rawName = club.name.trim()
    val rawAddr = club.address.trim()
    val title = rawName.substringBefore(',').trim().ifBlank { rawName }
    val address = when {
        rawAddr.isNotBlank() && !rawAddr.equals(rawName, ignoreCase = true) -> rawAddr
        rawName.contains(',') -> rawName.substringAfter(',').trim()
        else -> rawAddr
    }
    return ClubDisplay(
        title = title,
        address = address,
        imageRes = R.drawable.registration_club_mall,
    )
}

@Composable
private fun ClubPickCard(
    title: String,
    address: String,
    @DrawableRes imageRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "clubCardScale")
    val borderColor by animateColorAsState(
        if (selected) Primary else Color.Transparent,
        label = "clubCardBorder",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = if (selected) 6.dp else 2.dp,
        border = BorderStroke(if (selected) 2.5.dp else 0.dp, borderColor),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.05f),
                                0.55f to Color.Black.copy(alpha = 0.15f),
                                1f to Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                )
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(Primary, PrimaryVariant)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = address.ifBlank { "Адрес уточняется" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF5C534E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Text(
                        text = "Выбран",
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
