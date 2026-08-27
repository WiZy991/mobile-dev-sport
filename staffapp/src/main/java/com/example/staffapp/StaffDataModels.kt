package com.example.staffapp

data class StaffAppData(
    val employeeId: Int = 0,
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
    val specializations: List<String> = emptyList(),
    val specializationsCatalog: List<String> = TrainerSpecializationCatalog.DEFAULT,
    val description: String,
    val phone: String = "",
    val rating: Float,
    val photoUrl: String?,
    val profileComplete: Boolean = true,
    val publicationStatus: String = "moderation",
    val publicationStatusLabel: String = "На модерации",
    val services: List<TrainerServiceItem> = emptyList(),
    val needsModeration: Boolean = false,
)

data class TrainerServiceItem(
    val name: String,
    val priceFrom: Int,
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
    /** Сервер: аренда не нужна или срок ещё действует (хотя бы один зал). */
    val rentalActive: Boolean = false,
    val offerUrl: String,
    val privacyUrl: String = "https://dobrozal.ru/doc/privacy",
    val docsUrl: String = "https://dobrozal.ru/doc",
    val rentalAmountKopecks: Int,
    val rentalAmountRub: Double,
    val rentalPlans: List<RentalPlan> = emptyList(),
    val rentalClubs: List<RentalClubOption> = emptyList(),
    val activeClubId: Int? = null,
    val rentalDays: Int = 30,
    val staffUserId: Int? = null,
    val profileComplete: Boolean = true,
    val profileMissing: List<String> = emptyList(),
    val specializationsCatalog: List<String> = TrainerSpecializationCatalog.DEFAULT,
) {
    val activeClub: RentalClubOption?
        get() = rentalClubs.firstOrNull { it.isActiveClub }
            ?: activeClubId?.let { id -> rentalClubs.firstOrNull { it.clubId == id } }

    /** Срок для QR: активный зал, иначе общий legacy. */
    val activeClubPaidUntil: String?
        get() = activeClub?.paidUntil ?: rentalPaidUntil

    val activeClubRentalOk: Boolean
        get() = when {
            !requiresRental -> true
            activeClub != null -> activeClub!!.rentalActive ||
                StaffRentalAccess.isPaidPeriodActive(activeClub!!.paidUntil)
            else -> rentalActive || StaffRentalAccess.isPaidPeriodActive(rentalPaidUntil)
        }
}

data class RentalClubOption(
    val clubId: Int,
    val name: String,
    val address: String,
    val amountKopecks: Int,
    val amountRub: Double,
    val paidUntil: String? = null,
    val rentalActive: Boolean = false,
    val isActiveClub: Boolean = false,
    val days: Int = 30,
    /** ascii | wiegand — как у клиентского клуба. */
    val entryQrFormat: String? = null,
) {
    val title: String
        get() = if (address.isNotBlank() && !name.contains(address, ignoreCase = true)) {
            "$name · $address"
        } else {
            name.ifBlank { address }
        }
}

data class RentalPlan(
    val months: Int,
    val label: String,
    val amountKopecks: Int,
    val amountRub: Double,
)

data class RentalPaymentItem(
    val id: Int,
    val status: String,
    val amountRub: Double,
    val durationMonths: Int,
    val paidAt: String?,
    val createdAt: String?,
    val clubId: Int? = null,
    val clubName: String? = null,
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
    val hasActiveBooking: Boolean = false,
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
    val isUpcoming: Boolean = false,
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
