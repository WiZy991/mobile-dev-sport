package com.example.staffapp.ui.work

data class MetricUi(val label: String, val value: String)

data class ListCardUi(
    val title: String,
    val subtitle: String = "",
    val meta: String = "",
    val badge: String? = null,
    val badgeColor: BadgeColor = BadgeColor.NEUTRAL,
    val clientId: Int? = null,
    val ticketId: Int? = null,
    val refType: String? = null,
    val feedId: Int? = null,
) {
    val isClickable: Boolean
        get() = clientId != null || ticketId != null || refType == "client" || refType == "ticket"
}

data class ProfileSectionUi(
    val key: String,
    val title: String,
    val hint: String = "",
)

enum class BadgeColor { NEUTRAL, SUCCESS, WARNING, ERROR, PRIMARY }

data class ActionUi(val id: String, val label: String)

data class DayChipUi(val date: String, val label: String, val count: Int, val selected: Boolean)

data class HomeTabUi(
    val greeting: String = "",
    val roleTitle: String = "",
    val metrics: List<MetricUi> = emptyList(),
    val showAdminButton: Boolean = false,
    val sectionTitle: String? = null,
    val items: List<ListCardUi> = emptyList(),
    val actions: List<ActionUi> = emptyList(),
    val loading: Boolean = false,
    val emptyMessage: String? = null,
)

data class ScheduleDayUi(
    val date: String,
    val weekdayLabel: String,
    val dayNumber: String,
    val sessionCount: Int,
    val selected: Boolean,
    val isToday: Boolean = false,
)

data class ScheduleSessionUi(
    val trainingId: String? = null,
    val title: String,
    val type: String,
    val typeLabel: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val trainer: String,
    val room: String,
    val bookedCount: Int? = null,
    val maxParticipants: Int? = null,
    val clientNames: List<String> = emptyList(),
    val bookings: List<ScheduleBookingUi> = emptyList(),
)

data class ScheduleBookingUi(
    val id: String,
    val clientName: String,
    val clientId: String? = null,
)

data class ScheduleTabUi(
    val days: List<ScheduleDayUi> = emptyList(),
    val sessions: List<ScheduleSessionUi> = emptyList(),
    val selectedTypeFilter: String? = null,
    val denied: Boolean = false,
    val deniedMessage: String = "",
    val loading: Boolean = false,
)

data class ClientsTabUi(
    val query: String = "",
    val summary: String = "",
    val items: List<ListCardUi> = emptyList(),
    val denied: Boolean = false,
    val deniedMessage: String = "",
    val loading: Boolean = false,
)

data class SupportTabUi(
    val newCount: Int = 0,
    val unreadCount: Int = 0,
    val filters: List<DayChipUi> = emptyList(),
    val notifications: List<ListCardUi> = emptyList(),
    val tickets: List<ListCardUi> = emptyList(),
    val ticketActions: Map<Int, List<ActionUi>> = emptyMap(),
    val actions: List<ActionUi> = emptyList(),
    val denied: Boolean = false,
    val deniedMessage: String = "",
    val loading: Boolean = false,
)

data class ProfileTabUi(
    val name: String = "",
    val email: String = "",
    val roleTitle: String = "",
    val sections: List<ProfileSectionUi> = emptyList(),
    val adminAvailable: Boolean = false,
    val showAdminButton: Boolean = false,
    val showTrainerProfileEdit: Boolean = false,
    val sectionTitle: String? = null,
    val items: List<ListCardUi> = emptyList(),
    val loading: Boolean = false,
)

data class WorkUiState(
    val selectedTab: Int = TAB_HOME,
    val screenTitle: String = "Главная",
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val showScheduleNav: Boolean = false,
    val showClientsNav: Boolean = false,
    val showSupportNav: Boolean = false,
    val home: HomeTabUi = HomeTabUi(),
    val schedule: ScheduleTabUi = ScheduleTabUi(),
    val clients: ClientsTabUi = ClientsTabUi(),
    val support: SupportTabUi = SupportTabUi(),
    val profile: ProfileTabUi = ProfileTabUi(),
    val assignDialog: AssignClientDialogUi? = null,
    val createSessionDialog: CreateSessionDialogUi? = null,
) {
    companion object {
        const val TAB_HOME = 1
        const val TAB_SCHEDULE = 2
        const val TAB_CLIENTS = 3
        const val TAB_PROFILE = 4
        const val TAB_SUPPORT = 5
    }
}
