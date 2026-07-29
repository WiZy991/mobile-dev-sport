package com.example.staffapp

data class StaffAppData(
    val employeeName: String,
    val employeeEmail: String,
    val roles: List<String> = emptyList(),
    val sections: List<String>,
    val metrics: Map<String, Int>,
)

data class TrainerPublicProfile(
    val id: String?,
    val name: String,
    val specialization: String,
    val description: String,
    val phone: String = "",
    val rating: Float,
    val photoUrl: String?,
)

data class StaffAdminData(
    val adminSections: List<String>,
    val adminMenu: Map<String, String>,
    val widgets: Map<String, Int>,
    val canWrite: Boolean,
)

data class SectionData(
    val mode: String,
    val section: String,
    val cards: Map<String, Int>,
)

data class ScheduleDay(
    val date: String,
    val label: String,
    val count: Int,
)

data class ScheduleBookingRow(
    val id: String,
    val clientName: String,
    val clientId: String?,
    val status: String,
)

data class ScheduleItem(
    val id: String?,
    val title: String,
    val trainer: String,
    val type: String,
    val date: String,
    val dayLabel: String,
    val startTime: String,
    val endTime: String,
    val startAt: String,
    val endAt: String,
    val room: String,
    val clientNames: List<String>,
    val bookings: List<ScheduleBookingRow> = emptyList(),
    val participants: String,
    val maxParticipants: Int? = null,
    val currentParticipants: Int? = null,
)

data class StaffOnboarding(
    val status: String,
    val registrationStatus: String,
    val requiresRental: Boolean,
    val rentalPaidUntil: String?,
    val offerUrl: String,
    val rentalAmountKopecks: Int,
    val rentalAmountRub: Double,
)

data class RentalPaymentResult(
    val paymentId: Int,
    val status: String,
    val paymentUrl: String?,
    val onboarding: StaffOnboarding,
)

data class ScheduleData(
    val days: List<ScheduleDay>,
    val items: List<ScheduleItem>,
)

data class FeedListItem(
    val title: String,
    val subtitle: String,
    val meta: String,
    val id: Int? = null,
    val refType: String? = null,
)

data class SupportTicketItem(
    val id: Int,
    val subject: String,
    val message: String,
    val category: String,
    val status: String,
    val contactEmail: String,
    val clientName: String,
    val clientPhone: String,
    val clientId: Int?,
    val createdAt: String,
)

data class ClientSummary(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
)

data class ClientSubscription(
    val plan: String,
    val status: String,
    val endDate: String?,
    val visitsUsed: Int,
    val visitsTotal: Int,
)

data class ClientDetailRow(
    val title: String,
    val meta: String,
)

data class ClientDetail(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val bonusPoints: Int,
    val isBlocked: Boolean,
    val subscription: ClientSubscription?,
    val recentBookings: List<ClientDetailRow>,
    val recentTickets: List<ClientDetailRow>,
)

data class SupportTicketsData(
    val items: List<SupportTicketItem>,
    val newCount: Int,
)

data class StaffNotificationItem(
    val id: Int,
    val type: String,
    val title: String,
    val body: String,
    val referenceId: String,
    val createdAt: String,
    val isRead: Boolean,
)

data class StaffNotificationsData(
    val items: List<StaffNotificationItem>,
    val unreadCount: Int,
)
