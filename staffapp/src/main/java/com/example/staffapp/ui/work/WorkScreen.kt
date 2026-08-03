package com.example.staffapp.ui.work

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.staffapp.RequestNotificationPermission
import com.example.staffapp.ui.components.StaffActionButtons
import com.example.staffapp.ui.components.StaffChipRow
import com.example.staffapp.ui.components.StaffEmptyState
import com.example.staffapp.ui.components.StaffErrorState
import com.example.staffapp.ui.components.StaffHeroCard
import com.example.staffapp.ui.components.StaffInfoBanner
import com.example.staffapp.ui.components.StaffListCard
import com.example.staffapp.ui.components.StaffLoadingState
import com.example.staffapp.ui.components.StaffMenuCard
import com.example.staffapp.ui.components.StaffMetricsRow
import com.example.staffapp.ui.components.StaffPrimaryButton
import com.example.staffapp.ui.components.StaffSecondaryButton
import com.example.staffapp.ui.components.StaffSearchBar
import com.example.staffapp.ui.components.StaffSectionTitle
import com.example.staffapp.ui.theme.StaffOnSurfaceVariant
import com.example.staffapp.ui.theme.StaffPrimary

private data class NavItem(
    val tab: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(
    state: WorkUiState,
    onTabSelected: (Int) -> Unit,
    onLogout: () -> Unit,
    onAction: (String) -> Unit,
    onScheduleDaySelected: (String) -> Unit,
    onScheduleTypeFilterSelected: (String?) -> Unit,
    onSchedulePrevPeriod: () -> Unit = {},
    onScheduleNextPeriod: () -> Unit = {},
    onSupportFilterSelected: (String) -> Unit,
    onClientSearchQueryChange: (String) -> Unit,
    onClientSearch: () -> Unit,
    onClientsActiveFilterToggle: () -> Unit = {},
    onListCardClick: (ListCardUi) -> Unit,
    onProfileSectionClick: (String) -> Unit,
    onScheduleSessionClick: (ScheduleSessionUi) -> Unit = {},
    onAssignQueryChange: (String) -> Unit = {},
    onAssignSearch: () -> Unit = {},
    onAssignBook: (Int) -> Unit = {},
    onAssignCancelBooking: (String) -> Unit = {},
    onAssignOpenClient: (Int) -> Unit = {},
    onAssignEditSession: () -> Unit = {},
    onAssignDismiss: () -> Unit = {},
    onCreateSessionClick: () -> Unit = {},
    onCreateNameChange: (String) -> Unit = {},
    onCreateDateChange: (String) -> Unit = {},
    onCreateStartTimeChange: (String) -> Unit = {},
    onCreateDurationChange: (Int) -> Unit = {},
    onCreateRoomChange: (String) -> Unit = {},
    onCreateConfirm: () -> Unit = {},
    onCreateDismiss: () -> Unit = {},
    onNotificationPermissionResult: (Boolean) -> Unit = {},
) {
    // Системный диалог Android 13+ — сразу при открытии рабочего экрана (как в клиентском приложении).
    RequestNotificationPermission(onResult = onNotificationPermissionResult)

    // «Назад» с любой вкладки возвращает на Главную, и только с Главной закрывает приложение.
    BackHandler(enabled = state.selectedTab != WorkUiState.TAB_HOME) {
        onTabSelected(WorkUiState.TAB_HOME)
    }

    state.assignDialog?.let { dialog ->
        AssignClientDialog(
            state = dialog,
            onQueryChange = onAssignQueryChange,
            onSearch = onAssignSearch,
            onBookClient = onAssignBook,
            onCancelBooking = onAssignCancelBooking,
            onOpenClient = onAssignOpenClient,
            onEditSession = onAssignEditSession,
            onDismiss = onAssignDismiss,
        )
    }
    state.createSessionDialog?.let { dialog ->
        CreateSessionDialog(
            state = dialog,
            onNameChange = onCreateNameChange,
            onDateChange = onCreateDateChange,
            onStartTimeChange = onCreateStartTimeChange,
            onDurationChange = onCreateDurationChange,
            onRoomChange = onCreateRoomChange,
            onCreate = onCreateConfirm,
            onDismiss = onCreateDismiss,
        )
    }
    val navItems = buildList {
        add(NavItem(WorkUiState.TAB_HOME, "Главная", Icons.Filled.Home, Icons.Outlined.Home))
        if (state.showScheduleNav) {
            add(NavItem(WorkUiState.TAB_SCHEDULE, "Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth))
        }
        if (state.showClientsNav) {
            add(NavItem(WorkUiState.TAB_CLIENTS, "Клиенты", Icons.Filled.People, Icons.Outlined.People))
        }
        add(NavItem(WorkUiState.TAB_PROFILE, "Профиль", Icons.Filled.Person, Icons.Outlined.Person))
        if (state.showSupportNav) {
            add(NavItem(WorkUiState.TAB_SUPPORT, "Обращения", Icons.Filled.SupportAgent, Icons.Outlined.SupportAgent))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Сотрудник",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            state.screenTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StaffPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            if (state.selectedTab == WorkUiState.TAB_SCHEDULE && state.showScheduleNav && !state.schedule.denied) {
                FloatingActionButton(
                    onClick = onCreateSessionClick,
                    containerColor = StaffPrimary,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Создать запись")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.ui.graphics.Color.White,
                tonalElevation = 8.dp,
            ) {
                navItems.forEach { item ->
                    val selected = state.selectedTab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onTabSelected(item.tab) },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = StaffPrimary,
                            selectedTextColor = StaffPrimary,
                            indicatorColor = StaffPrimary.copy(alpha = 0.12f),
                            unselectedIconColor = StaffOnSurfaceVariant,
                            unselectedTextColor = StaffOnSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.selectedTab) {
                WorkUiState.TAB_HOME -> HomeTabContent(state.home, onAction, onListCardClick)
                WorkUiState.TAB_SCHEDULE -> StaffScheduleTabContent(
                    schedule = state.schedule,
                    onDaySelected = onScheduleDaySelected,
                    onTypeFilterSelected = onScheduleTypeFilterSelected,
                    onPrevPeriod = onSchedulePrevPeriod,
                    onNextPeriod = onScheduleNextPeriod,
                    onSessionClick = onScheduleSessionClick,
                )
                WorkUiState.TAB_CLIENTS -> ClientsTabContent(
                    state.clients,
                    onClientSearchQueryChange,
                    onClientSearch,
                    onClientsActiveFilterToggle,
                    onListCardClick,
                )
                WorkUiState.TAB_SUPPORT -> SupportTabContent(
                    state.support,
                    onSupportFilterSelected,
                    onAction,
                    onListCardClick,
                )
                WorkUiState.TAB_PROFILE -> ProfileTabContent(
                    state.profile,
                    onAction,
                    onListCardClick,
                    onProfileSectionClick,
                )
            }
            state.errorMessage?.let { msg ->
                StaffErrorState(
                    message = msg,
                    onRetry = { onAction("retry") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    home: HomeTabUi,
    onAction: (String) -> Unit,
    onListCardClick: (ListCardUi) -> Unit,
) {
    if (home.loading && home.greeting.isBlank()) {
        StaffLoadingState()
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StaffHeroCard(
                title = home.greeting.ifBlank { "Добро пожаловать" },
                subtitle = home.roleTitle,
            )
        }
        if (home.needNotificationsPermission) {
            item {
                StaffInfoBanner("Push-уведомления выключены — вы не получите оповещения о обращениях и записях.")
            }
            item {
                StaffPrimaryButton(
                    text = "Включить уведомления",
                    onClick = { onAction("enable_notifications") },
                )
            }
        }
        if (home.metrics.isNotEmpty()) {
            item { StaffMetricsRow(home.metrics) }
        }
        if (home.showAdminButton) {
            item {
                StaffPrimaryButton(
                    text = "Открыть админку",
                    onClick = { onAction("open_admin") },
                )
            }
        }
        if (home.loading) {
            item { StaffLoadingState() }
        } else {
            home.sections.forEach { section ->
                item { StaffSectionTitle(section.title) }
                if (section.items.isEmpty() && section.emptyMessage != null) {
                    item { StaffEmptyState(section.emptyMessage) }
                } else {
                    items(section.items) { item ->
                        StaffListCard(
                            item = item,
                            onClick = if (item.isClickable) {{ onListCardClick(item) }} else null,
                        )
                    }
                }
            }
        }
        if (home.actions.isNotEmpty()) {
            item { StaffActionButtons(home.actions, onAction) }
        }
    }
}

@Composable
private fun ClientsTabContent(
    clients: ClientsTabUi,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onActiveFilterToggle: () -> Unit,
    onListCardClick: (ListCardUi) -> Unit,
) {
    if (clients.denied) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { StaffEmptyState(clients.deniedMessage) }
        }
        return
    }
    Column {
        StaffSearchBar(
            query = clients.query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !clients.onlyActiveBooking,
                onClick = { if (clients.onlyActiveBooking) onActiveFilterToggle() },
                label = { Text("Все") },
            )
            FilterChip(
                selected = clients.onlyActiveBooking,
                onClick = { if (!clients.onlyActiveBooking) onActiveFilterToggle() },
                label = { Text("С активной записью") },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (clients.loading) {
                item { StaffLoadingState("Поиск клиентов...") }
            } else if (clients.items.isEmpty()) {
                item { StaffEmptyState("Клиенты не найдены") }
            } else {
                if (clients.summary.isNotBlank()) {
                    item { StaffInfoBanner(clients.summary) }
                }
                items(clients.items) { item ->
                    StaffListCard(item = item, onClick = { onListCardClick(item) })
                }
            }
        }
    }
}

@Composable
private fun SupportTabContent(
    support: SupportTabUi,
    onFilterSelected: (String) -> Unit,
    onAction: (String) -> Unit,
    onListCardClick: (ListCardUi) -> Unit,
) {
    if (support.denied) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item { StaffEmptyState(support.deniedMessage) }
        }
        return
    }
    if (support.filters.isNotEmpty()) {
        StaffChipRow(chips = support.filters, onChipClick = onFilterSelected)
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (support.loading) {
            item { StaffLoadingState("Загрузка обращений...") }
        } else {
            item {
                StaffInfoBanner(
                    buildString {
                        append("Новых обращений: ${support.newCount}")
                        if (support.unreadCount > 0) {
                            append(" · Непрочитанных: ${support.unreadCount}")
                        }
                    },
                )
            }
            if (support.actions.isNotEmpty()) {
                item { StaffActionButtons(support.actions, onAction) }
            }
            if (support.notifications.isNotEmpty()) {
                item { StaffSectionTitle("Уведомления") }
                items(support.notifications) { item ->
                    StaffListCard(item = item)
                }
            }
            item { StaffSectionTitle("Обращения") }
            if (support.tickets.isEmpty()) {
                item { StaffEmptyState("Обращений по фильтру нет") }
            } else {
                items(support.tickets) { ticket ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StaffListCard(
                            item = ticket,
                            onClick = if (ticket.isClickable) {{ onListCardClick(ticket) }} else null,
                        )
                        ticket.ticketId?.let { id -> support.ticketActions[id] }?.let { actions ->
                            StaffActionButtons(actions, onAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTabContent(
    profile: ProfileTabUi,
    onAction: (String) -> Unit,
    onListCardClick: (ListCardUi) -> Unit,
    onProfileSectionClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Пункт 27 репорта: раздел «Профиль» показывает карточку сотрудника
        // (фото и данные), а не дублирует нижнее меню.
        item { ProfileHeaderCard(profile) }
        item {
            StaffInfoBanner(
                "Email и пароль для входа можно изменить только через администратора клуба — " +
                    "обратитесь на ресепшн.",
                color = StaffOnSurfaceVariant,
            )
        }
        profile.rentalPaidUntilLabel?.let { rental ->
            item { StaffInfoBanner(rental, color = StaffPrimary) }
        }
        if (profile.showTrainerProfileEdit) {
            item {
                StaffPrimaryButton(
                    text = "Редактировать профиль тренера",
                    onClick = { onAction("edit_trainer_profile") },
                )
            }
        }
        item { StaffSectionTitle("Документы") }
        item {
            StaffPrimaryButton(
                text = "Публичная оферта",
                onClick = { onAction("open_offer") },
            )
        }
        item {
            StaffSecondaryButton(
                text = "Политика конфиденциальности",
                onClick = { onAction("open_privacy") },
            )
        }
        item {
            StaffSecondaryButton(
                text = "Все документы клуба",
                onClick = { onAction("open_docs") },
            )
        }
        if (profile.showAdminButton) {
            item {
                StaffPrimaryButton(text = "Открыть админку", onClick = { onAction("open_admin") })
            }
        }
        // Тренеру не показываем упоминание админки вовсе, чтобы не создавать
        // впечатление, что доступ нужно как-то «разблокировать».
        if (profile.adminAvailable) {
            item { StaffInfoBanner("Админка CRM доступна") }
        }
        profile.sectionTitle?.let { title ->
            item { StaffSectionTitle(title) }
        }
        if (profile.loading) {
            item { StaffLoadingState() }
        } else if (profile.items.isNotEmpty()) {
            items(profile.items) { item ->
                StaffListCard(
                    item = item,
                    onClick = if (item.isClickable) {{ onListCardClick(item) }} else null,
                )
            }
        }
        if (profile.sections.isNotEmpty()) {
            item {
                StaffMenuCard(
                    title = "Доступные разделы",
                    items = profile.sections.map { section ->
                        SectionIcons.forSection(section.key) to (section.title to section.hint)
                    },
                    onItemClick = { index -> onProfileSectionClick(profile.sections[index].key) },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ProfileHeaderCard(profile: ProfileTabUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(StaffPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!profile.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = "Фото профиля",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = StaffPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile.name.ifBlank { "Сотрудник" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (profile.roleTitle.isNotBlank()) {
                        Text(
                            text = profile.roleTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StaffOnSurfaceVariant,
                        )
                    }
                }
            }
            ProfileInfoRow(label = "Email", value = profile.email)
            ProfileInfoRow(label = "Телефон", value = profile.phone)
            ProfileInfoRow(label = "Специализация", value = profile.specialization)
            if (profile.description.isNotBlank()) {
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StaffOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = StaffOnSurfaceVariant,
            modifier = Modifier.width(130.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
