package com.fitnessclub.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitnessclub.app.data.api.ClubPromotion
import coil.compose.AsyncImage
import com.fitnessclub.app.ui.components.OccupancyCard
import com.fitnessclub.app.ui.components.SecondaryButton
import com.fitnessclub.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSchedule: () -> Unit,
    onNavigateToPersonalTraining: () -> Unit,
    onNavigateToShop: () -> Unit,
    onNavigateToSubscriptionPlans: () -> Unit = onNavigateToShop,
    onNavigateToTrainers: () -> Unit,
    onNavigateToClubInfo: () -> Unit,
    onNavigateToLockers: () -> Unit = {},
    onNavigateToNotifications: () -> Unit,
    onNavigateToQrCode: () -> Unit = {},
    onNavigateToTrainingDetails: (String) -> Unit = {},
    onNavigateToTrainingDiary: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToReferral: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Доброзал",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (uiState.unreadNotifications > 0) {
                                Badge { Text(uiState.unreadNotifications.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Default.Notifications, contentDescription = "Уведомления")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Promo banner
            item(key = "promo") {
                PromoBanner(
                    promotions = uiState.promotions,
                    onClick = { promo ->
                        when (promo.actionType.lowercase()) {
                            "subscriptions" -> onNavigateToSubscriptionPlans()
                            "none" -> Unit
                            else -> onNavigateToShop()
                        }
                    }
                )
            }
            
            // Occupancy widget
            item(key = "occupancy") {
                OccupancyCard(
                    current = uiState.occupancyCurrent,
                    max = uiState.occupancyMax,
                    percentage = uiState.occupancyPercentage,
                    status = uiState.occupancyStatus,
                    onRefresh = { viewModel.loadOccupancy() }
                )
            }
            
            // Quick menu
            item(key = "quickmenu") {
                QuickMenuSection(
                    onQrCode = onNavigateToQrCode,
                    onPersonalTraining = onNavigateToPersonalTraining,
                    onSchedule = onNavigateToSchedule,
                    onShop = onNavigateToShop,
                    onClubInfo = onNavigateToClubInfo,
                    onLockers = onNavigateToLockers,
                    onTrainers = onNavigateToTrainers,
                    onTrainingDiary = onNavigateToTrainingDiary
                )
            }
            
            // Upcoming trainings
            if (uiState.upcomingTrainings.isNotEmpty()) {
                item(key = "upcoming_title") {
                    SectionTitle("Ближайшие тренировки")
                }
                item(key = "upcoming") {
                    UpcomingTrainingsSection(
                        trainings = uiState.upcomingTrainings,
                        onClick = onNavigateToTrainingDetails
                    )
                }
            }
            
            // Special offers
            item(key = "offers_title") {
                SectionTitle("Специальные предложения")
            }
            item(key = "offers") {
                SpecialOffersSection(
                    onShopClick = onNavigateToShop,
                    onReferralClick = onNavigateToReferral
                )
            }
        }
    }
}

@Composable
private fun PromoBanner(
    promotions: List<ClubPromotion>,
    onClick: (ClubPromotion) -> Unit
) {
    if (promotions.isEmpty()) return
    var current by remember(promotions) { mutableStateOf(0) }
    val currentPromotion = promotions[current.coerceIn(0, promotions.lastIndex)]

    LaunchedEffect(promotions, current) {
        if (promotions.size <= 1) return@LaunchedEffect
        delay(4500)
        current = (current + 1) % promotions.size
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick(currentPromotion) },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            parsePromoColor(currentPromotion.bgFrom, Primary),
                            parsePromoColor(currentPromotion.bgTo, AccentBlue)
                        )
                    )
                )
        ) {
            if (!currentPromotion.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = currentPromotion.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = currentPromotion.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentPromotion.subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onClick(currentPromotion) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = Primary
                    )
                ) {
                    Text(currentPromotion.buttonText.ifBlank { "Подробнее" }, style = MaterialTheme.typography.labelLarge)
                }
                if (promotions.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        promotions.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == current) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == current) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parsePromoColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }
}

@Composable
private fun QuickMenuSection(
    onQrCode: () -> Unit,
    onPersonalTraining: () -> Unit,
    onSchedule: () -> Unit,
    onShop: () -> Unit,
    onClubInfo: () -> Unit,
    onLockers: () -> Unit,
    onTrainers: () -> Unit,
    onTrainingDiary: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            QuickMenuItem(
                icon = Icons.Default.QrCode2,
                title = "Вход в зал",
                subtitle = "Показать QR-код для прохода",
                onClick = onQrCode
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.Person,
                title = "Записаться на персональную тренировку",
                subtitle = "Индивидуальное занятие с тренером",
                onClick = onPersonalTraining
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.CalendarMonth,
                title = "Расписание",
                subtitle = "Групповые программы",
                onClick = onSchedule
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.ShoppingCart,
                title = "Приобрести",
                subtitle = "Карты, Абонементы, Услуги",
                onClick = onShop
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.LocationOn,
                title = "Мы на карте",
                subtitle = "Покажем кратчайший путь",
                onClick = onClubInfo
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.LockOpen,
                title = "Шкафчики",
                subtitle = "Бронирование и QR-код для открытия",
                onClick = onLockers
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.People,
                title = "Наша команда",
                subtitle = "Опытные тренеры",
                onClick = onTrainers
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            QuickMenuItem(
                icon = Icons.Default.MenuBook,
                title = "Дневник тренировок",
                subtitle = "Упражнения, прогресс и статистика",
                onClick = onTrainingDiary
            )
        }
    }
}

@Composable
private fun QuickMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun UpcomingTrainingsSection(
    trainings: List<UpcomingTraining>,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        trainings.forEach { training ->
            UpcomingTrainingCard(training = training, onClick = { onClick(training.id) })
        }
    }
}

@Composable
private fun UpcomingTrainingCard(
    training: UpcomingTraining,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = onClick,
                    label = { Text(training.time) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = training.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = training.trainer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = training.room,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpecialOffersSection(
    onShopClick: () -> Unit,
    onReferralClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OfferCard(
            title = "Пробная тренировка",
            description = "Бесплатно для новых клиентов",
            buttonText = "Получить",
            onClick = onShopClick
        )
        OfferCard(
            title = "Приведи друга",
            description = "500 бонусов за каждого друга",
            buttonText = "Узнать больше",
            onClick = onReferralClick
        )
        OfferCard(
            title = "-20% на годовой",
            description = "При покупке до конца месяца",
            buttonText = "Купить",
            onClick = onShopClick
        )
    }
}

@Composable
private fun OfferCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(180.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

data class UpcomingTraining(
    val id: String,
    val name: String,
    val time: String,
    val trainer: String,
    val room: String
)
