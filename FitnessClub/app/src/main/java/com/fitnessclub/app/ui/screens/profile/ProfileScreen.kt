package com.fitnessclub.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessclub.app.data.api.Achievement
import com.fitnessclub.app.data.config.Brand
import com.fitnessclub.app.data.model.Subscription
import com.fitnessclub.app.data.model.SubscriptionStatus
import com.fitnessclub.app.ui.components.PrimaryButton
import com.fitnessclub.app.ui.components.SecondaryButton
import com.fitnessclub.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onNavigateToQrCode: () -> Unit = {},
    onNavigateToSubscriptionPlans: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToDocuments: () -> Unit = {},
    onNavigateToPurchaseHistory: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSelectClub: () -> Unit = {},
    onNavigateToHallVisits: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBonusComingSoonDialog by remember { mutableStateOf(false) }
    var freezeTarget by remember { mutableStateOf<Subscription?>(null) }
    var showSubscriptionHistory by remember { mutableStateOf(false) }
    var showAllActive by remember { mutableStateOf(false) }

    // В «Моих» — только абонемент выбранного клуба. Остальные активные — отдельным списком.
    val preferredClubId = uiState.preferredClubId ?: uiState.user?.clubId
    val todayIso = remember { java.time.LocalDate.now().toString() }
    val activeSubscriptions = remember(uiState.subscriptions, todayIso) {
        uiState.subscriptions.filter { it.isCurrentOn(todayIso) }
    }
    val hasPreferredClub = preferredClubId != null && preferredClubId > 0
    val subscriptionsHere = remember(activeSubscriptions, preferredClubId) {
        if (!hasPreferredClub) activeSubscriptions
        else activeSubscriptions.filter { it.clubId == preferredClubId }
    }
    val subscriptionsElsewhere = remember(activeSubscriptions, preferredClubId) {
        if (!hasPreferredClub) emptyList()
        else activeSubscriptions.filter { it.clubId != preferredClubId }
    }
    val archivedSubscriptions = remember(uiState.subscriptions, todayIso) {
        uiState.subscriptions.filterNot { it.isCurrentOn(todayIso) }
    }
    
    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.LoggedOut -> onLogout()
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Профиль",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = { showLogoutDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Выйти",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            windowInsets = TopAppBarDefaults.windowInsets,
        )
        
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User info card
                item {
                    val addressLine = uiState.clubAddressLine
                    if (!addressLine.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = addressLine,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    UserInfoCard(
                        name = uiState.user?.name ?: "Загрузка...",
                        email = uiState.user?.email ?: "",
                        phone = uiState.user?.phone ?: "",
                        onEditClick = onNavigateToEditProfile
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SwitchClubButton(
                        currentClubLabel = uiState.clubAddressLine,
                        onClick = onNavigateToSelectClub,
                    )
                }
                
                if (uiState.stats != null) {
                    item {
                        GamificationCard(
                            totalVisits = uiState.stats!!.totalVisits,
                            streakDays = uiState.stats!!.streakDays,
                            achievements = uiState.stats!!.unlockedAchievements,
                        )
                    }
                    item {
                        HallVisitsCard(
                            lastVisitAt = uiState.stats!!.lastVisitAt,
                            recentVisits = uiState.stats!!.visits,
                            recentVisitDates = uiState.stats!!.visitDates,
                            totalVisits = uiState.stats!!.totalVisits,
                            onOpenHistory = onNavigateToHallVisits,
                        )
                    }
                }
                
                // Quick actions
                item {
                    QuickActionsRow(
                        onQrCodeClick = onNavigateToQrCode,
                        onBuySubscriptionClick = onNavigateToSubscriptionPlans,
                        onBonusesClick = { showBonusComingSoonDialog = true },
                        onNotificationsClick = onNavigateToNotifications
                    )
                }
                
                // Subscriptions section
                item {
                    Text(
                        text = "Мои абонементы",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Абонемент действует в том клубе, где куплен — он указан в карточке",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (uiState.isLoadingSubscriptions && subscriptionsHere.isEmpty() && subscriptionsElsewhere.isEmpty() && archivedSubscriptions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                if (subscriptionsHere.isEmpty() && subscriptionsElsewhere.isEmpty() && archivedSubscriptions.isEmpty() && !uiState.isLoadingSubscriptions) {
                    item {
                        EmptySubscriptionsCard(onBuyClick = onNavigateToSubscriptionPlans)
                    }
                } else {
                    if (subscriptionsHere.isNotEmpty()) {
                        items(subscriptionsHere, key = { it.id }) { subscription ->
                            SubscriptionCard(
                                subscription = subscription,
                                onFreezeClick = { freezeTarget = subscription },
                                onUnfreezeClick = { viewModel.unfreezeSubscription(subscription.id) },
                            )
                        }
                    } else if (!uiState.isLoadingSubscriptions && (subscriptionsElsewhere.isNotEmpty() || archivedSubscriptions.isNotEmpty())) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = "Сейчас нет активных абонементов",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (subscriptionsElsewhere.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = { showAllActive = !showAllActive },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (showAllActive) {
                                        "Скрыть активные абонементы в других клубах"
                                    } else {
                                        "Активные абонементы в других клубах (${subscriptionsElsewhere.size})"
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (showAllActive) {
                            items(subscriptionsElsewhere, key = { "other-${it.id}" }) { subscription ->
                                SubscriptionCard(
                                    subscription = subscription,
                                    onFreezeClick = { freezeTarget = subscription },
                                    onUnfreezeClick = { viewModel.unfreezeSubscription(subscription.id) },
                                )
                            }
                        }
                    }

                    if (archivedSubscriptions.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = { showSubscriptionHistory = !showSubscriptionHistory },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (showSubscriptionHistory) {
                                        "Скрыть историю абонементов (${archivedSubscriptions.size})"
                                    } else {
                                        "Показать историю абонементов (${archivedSubscriptions.size})"
                                    }
                                )
                            }
                        }
                    }

                    if (showSubscriptionHistory) {
                        items(archivedSubscriptions, key = { it.id }) { subscription ->
                            SubscriptionCard(
                                subscription = subscription,
                                onFreezeClick = { freezeTarget = subscription },
                                onUnfreezeClick = { viewModel.unfreezeSubscription(subscription.id) },
                            )
                        }
                    }
                }

                // Menu items
                item {
                    Text(
                        text = "Настройки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                item {
                    MenuCard(
                        onNotificationsClick = onNavigateToNotifications,
                        onSettingsClick = onNavigateToSettings,
                        onDocumentsClick = onNavigateToDocuments,
                        onPurchaseHistoryClick = onNavigateToPurchaseHistory,
                        onHelpClick = onNavigateToHelp,
                        onAboutClick = onNavigateToAbout
                    )
                }
            }
        }
    }
    
    // Bonus program coming soon
    if (showBonusComingSoonDialog) {
        AlertDialog(
            onDismissRequest = { showBonusComingSoonDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Warning,
                )
            },
            title = { Text("Бонусная программа") },
            text = {
                Text("Бонусная программа в разработке. Следите за новостями в приложении.")
            },
            confirmButton = {
                TextButton(onClick = { showBonusComingSoonDialog = false }) {
                    Text("Понятно")
                }
            },
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы уверены, что хотите выйти?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Freeze dialog
    freezeTarget?.let { subscription ->
        FreezeDialog(
            maxDaysLeft = subscription.freezeDaysLeft,
            onDismiss = { freezeTarget = null },
            onConfirm = { days ->
                viewModel.freezeSubscription(subscription.id, days)
                freezeTarget = null
            }
        )
    }

    uiState.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Ошибка") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun GamificationCard(
    totalVisits: Int,
    streakDays: Int,
    achievements: List<Achievement>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(alpha = 0.12f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$totalVisits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("посещений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$streakDays", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Text("дней подряд", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (achievements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                Text("Достижения", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                achievements.take(4).forEach { ach ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(ach.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(ach.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private val VisitRuLocale: java.util.Locale = java.util.Locale("ru")
private val VisitShortDateFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy")
private val VisitMonthStandaloneFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("LLLL", VisitRuLocale)
private val VisitWeekdayFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("EEEE", VisitRuLocale)
private val VisitTimeFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private val VisitShortMonths = arrayOf(
    "янв", "фев", "мар", "апр", "май", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

data class HallVisit(
    val date: java.time.LocalDate,
    val time: java.time.LocalTime?,
)

@Composable
private fun HallVisitsCard(
    lastVisitAt: String?,
    recentVisits: List<String>,
    recentVisitDates: List<String>,
    totalVisits: Int,
    onOpenHistory: () -> Unit,
) {
    val today = remember { java.time.LocalDate.now() }
    val entries = remember(recentVisits, recentVisitDates, lastVisitAt) {
        hallVisitEntries(recentVisits, recentVisitDates, lastVisitAt)
    }
    val latest = entries.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            Text(
                text = "Визиты в зал",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (latest == null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (totalVisits > 0) {
                        "История подтянется при следующем входе"
                    } else {
                        "После входа по QR здесь появится история"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                VisitHistoryRow(visit = latest, today = today, emphasized = true)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenHistory)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Все посещения",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = AccentOrange,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VisitHistoryRow(
    visit: HallVisit,
    today: java.time.LocalDate,
    emphasized: Boolean,
) {
    val weekday = visitWeekdayLabel(visit.date, today)
    val dateLine = visitDateLine(visit.date)
    val timeLabel = visit.time?.format(VisitTimeFormatter)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (emphasized) {
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentOrange.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                } else {
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VisitDateTile(
            dayOfMonth = visit.date.dayOfMonth,
            monthShort = VisitShortMonths[visit.date.monthValue - 1],
            emphasized = emphasized,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (emphasized) {
                Text(
                    text = "Последний вход",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentOrange,
                )
            }
            Text(
                text = weekday,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dateLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (timeLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeLabel,
                style = (if (emphasized) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                }).copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (emphasized) AccentOrange else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun VisitDateTile(
    dayOfMonth: Int,
    monthShort: String,
    emphasized: Boolean,
) {
    val numberColor = if (emphasized) Color.White else AccentOrange
    val monthColor = if (emphasized) Color.White.copy(alpha = 0.88f) else AccentOrange.copy(alpha = 0.78f)
    Box(
        modifier = Modifier
            .size(if (emphasized) 56.dp else 44.dp)
            .clip(RoundedCornerShape(if (emphasized) 18.dp else 14.dp))
            .background(if (emphasized) AccentOrange else AccentOrange.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayOfMonth.toString(),
                color = numberColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (emphasized) 22.sp else 16.sp,
                    lineHeight = if (emphasized) 24.sp else 18.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = monthShort,
                color = monthColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (emphasized) 11.sp else 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}

internal fun hallVisitEntries(
    recentVisits: List<String>,
    recentVisitDates: List<String>,
    lastVisitAt: String?,
): List<HallVisit> {
    val parsed = recentVisits.mapNotNull { parseHallVisit(it) }.map { it.normalized() }
    val base = when {
        parsed.isNotEmpty() -> parsed
        else -> {
            val dates = recentVisitDates.mapNotNull { iso ->
                runCatching { HallVisit(java.time.LocalDate.parse(iso), null) }.getOrNull()
            }
            if (dates.isNotEmpty()) dates else listOfNotNull(parseHallVisit(lastVisitAt)?.normalized())
        }
    }
    return applyLastVisitTime(base, lastVisitAt)
        .sortedWith(compareByDescending<HallVisit> { it.date }.thenByDescending { it.time })
}

private fun HallVisit.normalized(): HallVisit {
    val minute = time?.withSecond(0)?.withNano(0)
    return if (minute == time) this else copy(time = minute)
}

private fun applyLastVisitTime(entries: List<HallVisit>, lastVisitAt: String?): List<HallVisit> {
    val last = parseHallVisit(lastVisitAt)?.normalized() ?: return entries
    val minute = last.time ?: return entries
    if (entries.any { it.date == last.date && it.time == minute }) return entries
    val missingTime = entries.indexOfFirst { it.date == last.date && it.time == null }
    if (missingTime >= 0) {
        return entries.toMutableList().also { it[missingTime] = HallVisit(last.date, minute) }
    }
    return listOf(HallVisit(last.date, minute)) + entries
}

private fun parseHallVisit(iso: String?): HallVisit? {
    if (iso.isNullOrBlank()) return null
    runCatching {
        val local = java.time.OffsetDateTime.parse(iso).toLocalDateTime()
        return HallVisit(local.toLocalDate(), local.toLocalTime())
    }
    runCatching {
        val local = java.time.LocalDateTime.parse(iso)
        return HallVisit(local.toLocalDate(), local.toLocalTime())
    }
    return runCatching { HallVisit(java.time.LocalDate.parse(iso.take(10)), null) }.getOrNull()
}

internal fun visitDateLine(date: java.time.LocalDate): String = date.format(VisitShortDateFormatter)

internal fun visitMonthHeading(date: java.time.LocalDate, today: java.time.LocalDate): String {
    val month = date.format(VisitMonthStandaloneFormatter).replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(VisitRuLocale) else ch.toString()
    }
    return if (date.year == today.year) month else "$month ${date.year}"
}

internal fun visitWeekdayLabel(date: java.time.LocalDate, today: java.time.LocalDate): String {
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(VisitWeekdayFormatter).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(VisitRuLocale) else ch.toString()
        }
    }
}

@Composable
private fun QuickActionsRow(
    onQrCodeClick: () -> Unit,
    onBuySubscriptionClick: () -> Unit,
    onBonusesClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.Default.QrCode2,
            label = "QR-код",
            onClick = onQrCodeClick
        )
        QuickActionButton(
            icon = Icons.Default.ShoppingCart,
            label = if (Brand.onlinePaymentEnabled) "Купить" else "Абонементы",
            onClick = onBuySubscriptionClick
        )
        QuickActionButton(
            icon = Icons.Default.People,
            label = "Друзьям",
            onClick = onBonusesClick
        )
        QuickActionButton(
            icon = Icons.Default.Notifications,
            label = "Уведомления",
            onClick = onNotificationsClick
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchClubButton(
    currentClubLabel: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Primary, PrimaryVariant, Color(0xFFFF8F5A)),
                ),
            )
            .clickable(onClick = onClick)
            .padding(1.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.5.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Primary.copy(alpha = 0.2f), Primary.copy(alpha = 0.08f))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Выбрать другой клуб",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val hint = currentClubLabel?.takeIf { it.isNotBlank() }
                Text(
                    text = if (hint != null) "Сейчас: $hint" else "Сменить зал для абонементов и покупки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Primary,
            )
        }
    }
}

@Composable
private fun UserInfoCard(
    name: String,
    email: String,
    phone: String,
    onEditClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редактировать",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onFreezeClick: () -> Unit,
    onUnfreezeClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                SubscriptionStatusChip(status = subscription.status)
            }

            if (!subscription.clubName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subscription.clubName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Dates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Начало",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = subscription.startDate.take(10),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Окончание",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = subscription.endDate?.take(10) ?: "Без срока",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Visits info if limited
            subscription.visitsLeft?.let { visitsLeft ->
                Spacer(modifier = Modifier.height(12.dp))
                
                LinearProgressIndicator(
                    progress = { (subscription.visitsUsed.toFloat() / (subscription.visitsTotal ?: 1)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Осталось посещений: $visitsLeft из ${subscription.visitsTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Freeze info
            if (subscription.freezeDaysTotal > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Дней заморозки: ${subscription.freezeDaysLeft} из ${subscription.freezeDaysTotal}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Action buttons
            if (subscription.status == SubscriptionStatus.ACTIVE && subscription.freezeDaysLeft > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                
                SecondaryButton(
                    onClick = onFreezeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Заморозить")
                }
            }
            
            if (subscription.status == SubscriptionStatus.FROZEN) {
                Spacer(modifier = Modifier.height(12.dp))
                
                PrimaryButton(
                    onClick = onUnfreezeClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Разморозить")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionStatusChip(status: SubscriptionStatus) {
    val (backgroundColor, textColor, text) = when (status) {
        SubscriptionStatus.ACTIVE -> Triple(
            Success.copy(alpha = 0.1f),
            Success,
            "Активен"
        )
        SubscriptionStatus.FROZEN -> Triple(
            AccentBlue.copy(alpha = 0.1f),
            AccentBlue,
            "Заморожен"
        )
        SubscriptionStatus.EXPIRED -> Triple(
            Error.copy(alpha = 0.1f),
            Error,
            "Истёк"
        )
        SubscriptionStatus.PENDING -> Triple(
            Warning.copy(alpha = 0.1f),
            Warning,
            "Ожидание"
        )
        SubscriptionStatus.CANCELLED -> Triple(
            Error.copy(alpha = 0.1f),
            Error,
            "Отменён"
        )
    }
    
    Surface(
        modifier = Modifier.widthIn(min = 72.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptySubscriptionsCard(onBuyClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CardMembership,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "У вас нет активных абонементов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(onClick = onBuyClick) {
                Text(if (Brand.onlinePaymentEnabled) "Купить абонемент" else "Смотреть абонементы")
            }
        }
    }
}

@Composable
private fun MenuCard(
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDocumentsClick: () -> Unit = {},
    onPurchaseHistoryClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            MenuItem(
                icon = Icons.Default.Notifications,
                title = "Уведомления",
                onClick = onNotificationsClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Description,
                title = "Документы",
                onClick = onDocumentsClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.ReceiptLong,
                title = "История покупок",
                onClick = onPurchaseHistoryClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Settings,
                title = "Настройки",
                onClick = onSettingsClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Help,
                title = "Помощь",
                onClick = onHelpClick
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            MenuItem(
                icon = Icons.Default.Info,
                title = "О приложении",
                onClick = onAboutClick
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FreezeDialog(
    maxDaysLeft: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val defaultDays = minOf(7, maxDaysLeft).coerceAtLeast(1)
    var days by remember(maxDaysLeft) { mutableStateOf(defaultDays.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заморозить абонемент") },
        text = {
            Column {
                Text("Укажите количество дней заморозки (доступно: $maxDaysLeft):")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = days,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }
                        if (filtered.isEmpty()) {
                            days = ""
                            return@OutlinedTextField
                        }
                        val n = filtered.toIntOrNull() ?: return@OutlinedTextField
                        days = minOf(n, maxDaysLeft).toString()
                    },
                    label = { Text("Дни") },
                    singleLine = true,
                    supportingText = { Text("Максимум $maxDaysLeft дн.") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    days.toIntOrNull()?.takeIf { it in 1..maxDaysLeft }?.let { onConfirm(it) }
                }
            ) {
                Text("Заморозить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
