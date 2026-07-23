package com.example.staffapp.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onSupportFilterSelected: (String) -> Unit,
    onClientSearchQueryChange: (String) -> Unit,
    onClientSearch: () -> Unit,
    onListCardClick: (ListCardUi) -> Unit,
    onProfileSectionClick: (String) -> Unit,
    onScheduleSessionClick: (ScheduleSessionUi) -> Unit = {},
    onAssignQueryChange: (String) -> Unit = {},
    onAssignSearch: () -> Unit = {},
    onAssignBook: (Int) -> Unit = {},
    onAssignCancelBooking: (String) -> Unit = {},
    onAssignDismiss: () -> Unit = {},
) {
    state.assignDialog?.let { dialog ->
        AssignClientDialog(
            state = dialog,
            onQueryChange = onAssignQueryChange,
            onSearch = onAssignSearch,
            onBookClient = onAssignBook,
            onCancelBooking = onAssignCancelBooking,
            onDismiss = onAssignDismiss,
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
                    Text(
                        state.screenTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StaffPrimary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    actionIconContentColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
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
                    onSessionClick = onScheduleSessionClick,
                )
                WorkUiState.TAB_CLIENTS -> ClientsTabContent(
                    state.clients,
                    onClientSearchQueryChange,
                    onClientSearch,
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
        home.sectionTitle?.let { title ->
            item { StaffSectionTitle(title) }
        }
        if (home.loading) {
            item { StaffLoadingState() }
        } else if (home.items.isEmpty() && home.emptyMessage != null) {
            item { StaffEmptyState(home.emptyMessage) }
        } else {
            items(home.items) { item ->
                StaffListCard(
                    item = item,
                    onClick = if (item.isClickable) {{ onListCardClick(item) }} else null,
                )
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
        item {
            StaffHeroCard(
                title = profile.name.ifBlank { "Сотрудник" },
                subtitle = profile.roleTitle,
            )
        }
        if (profile.email.isNotBlank()) {
            item {
                StaffInfoBanner(profile.email, color = StaffOnSurfaceVariant)
            }
        }
        if (profile.showAdminButton) {
            item {
                StaffPrimaryButton(text = "Открыть админку", onClick = { onAction("open_admin") })
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
        item {
            StaffInfoBanner(
                if (profile.adminAvailable) "Админка CRM доступна" else "Админка для вашей должности недоступна",
            )
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
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
