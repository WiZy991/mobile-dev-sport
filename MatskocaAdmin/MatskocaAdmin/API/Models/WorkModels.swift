import Foundation

struct MetricUi: Identifiable {
    let id = UUID()
    let label: String
    let value: String
}

struct ListCardUi: Identifiable {
    let id = UUID()
    let title: String
    var subtitle: String = ""
    var meta: String = ""
    var badge: String?
    var badgeColor: BadgeColor = .neutral
    var clientId: Int?
    var ticketId: Int?
    var trainingId: String?
    var trainingDate: String?
    var refType: String?
    var feedId: Int?

    var isClickable: Bool {
        clientId != nil || ticketId != nil || trainingId != nil
            || refType == "client" || refType == "ticket"
    }
}

struct HomeSectionUi: Identifiable {
    let id = UUID()
    let title: String
    let items: [ListCardUi]
    var emptyMessage: String?
}

struct ProfileSectionUi: Identifiable {
    let id = UUID()
    let key: String
    let title: String
    var hint: String = ""
}

enum BadgeColor {
    case neutral, success, warning, error, primary
}

struct ActionUi: Identifiable {
    let id: String
    let label: String
}

struct DayChipUi: Identifiable {
    let id = UUID()
    let date: String
    let label: String
    let count: Int
    let selected: Bool
}

struct HomeTabUi {
    var greeting: String = ""
    var roleTitle: String = ""
    var metrics: [MetricUi] = []
    var showAdminButton: Bool = false
    var showEntryQr: Bool = false
    var entryQrStaffUserId: Int = 0
    var entryQrActive: Bool = false
    var entryQrBlockedMessage: String?
    var entryQrFormat: String = "ascii"
    var needNotificationsPermission: Bool = false
    var sections: [HomeSectionUi] = []
    var sectionTitle: String?
    var items: [ListCardUi] = []
    var actions: [ActionUi] = []
    var loading: Bool = false
    var emptyMessage: String?
}

struct ScheduleDayUi: Identifiable {
    let id = UUID()
    let date: String
    let weekdayLabel: String
    let dayNumber: String
    let sessionCount: Int
    let selected: Bool
    let isToday: Bool
}

struct ScheduleSessionUi: Identifiable {
    let id = UUID()
    var trainingId: String?
    var date: String = ""
    let title: String
    let type: String
    let typeLabel: String
    let startTime: String
    let endTime: String
    let durationMinutes: Int
    let trainer: String
    let room: String
    var bookedCount: Int?
    var maxParticipants: Int?
    var clientNames: [String] = []
    var bookings: [ScheduleBookingUi] = []
}

struct ScheduleBookingUi: Identifiable {
    let id: String
    let clientName: String
    var clientId: Int?
}

struct ScheduleTabUi {
    var days: [ScheduleDayUi] = []
    var sessions: [ScheduleSessionUi] = []
    var monthLabel: String = ""
    var selectedTypeFilter: String?
    var denied: Bool = false
    var deniedMessage: String = ""
    var loading: Bool = false
}

struct AssignClientDialogUi {
    var trainingId: String
    var sessionTitle: String
    var query: String = ""
    var clients: [ListCardUi] = []
    var booked: [ListCardUi] = []
    var loading: Bool = false
    var errorMessage: String?
}

struct ClientsTabUi {
    var query: String = ""
    var summary: String = ""
    var items: [ListCardUi] = []
    var onlyActiveBooking: Bool = false
    var denied: Bool = false
    var deniedMessage: String = ""
    var loading: Bool = false
}

struct SupportTabUi {
    var newCount: Int = 0
    var unreadCount: Int = 0
    var filters: [DayChipUi] = []
    var notifications: [ListCardUi] = []
    var tickets: [ListCardUi] = []
    var ticketActions: [Int: [ActionUi]] = [:]
    var actions: [ActionUi] = []
    var denied: Bool = false
    var deniedMessage: String = ""
    var loading: Bool = false
}

struct ProfileTabUi {
    var name: String = ""
    var email: String = ""
    var roleTitle: String = ""
    var phone: String = ""
    var specialization: String = ""
    var description: String = ""
    var photoUrl: String?
    var sections: [ProfileSectionUi] = []
    var adminAvailable: Bool = false
    var showAdminButton: Bool = false
    var showTrainerProfileEdit: Bool = false
    var showClubEntryQr: Bool = false
    var showRentalManage: Bool = false
    var showFeedback: Bool = true
    var rentalPaidUntilLabel: String?
    var paidRentalClubs: [RentalClubOption] = []
    var activeClubId: Int?
    var offerUrl: String = "https://dobrozal.ru/doc/offer"
    var privacyUrl: String = "https://dobrozal.ru/doc/privacy"
    var docsUrl: String = "https://dobrozal.ru/doc"
    var sectionTitle: String?
    var items: [ListCardUi] = []
    var loading: Bool = false
}

enum WorkTab: Int {
    case home = 1
    case schedule = 2
    case clients = 3
    case profile = 4
    case support = 5

    var title: String {
        switch self {
        case .home: return "Главная"
        case .schedule: return "Расписание"
        case .clients: return "Клиенты"
        case .profile: return "Профиль"
        case .support: return "Обращения"
        }
    }
}

struct WorkUiState {
    var selectedTab: WorkTab = .home
    var screenTitle: String = "Главная"
    var statusMessage: String?
    var errorMessage: String?
    var showScheduleNav: Bool = false
    var showClientsNav: Bool = false
    var showSupportNav: Bool = false
    var home: HomeTabUi = HomeTabUi()
    var schedule: ScheduleTabUi = ScheduleTabUi()
    var clients: ClientsTabUi = ClientsTabUi()
    var support: SupportTabUi = SupportTabUi()
    var profile: ProfileTabUi = ProfileTabUi()
    var assignDialog: AssignClientDialogUi?
}

struct RoleOptionUi: Identifiable, Equatable {
    var id: String { role }
    let label: String
    let role: String
}

struct LoginUiState {
    var email: String = ""
    var name: String = ""
    var password: String = ""
    var selectedRole: RoleOptionUi?
    var roles: [RoleOptionUi] = []
    var configSummary: String = ""
    var statusMessage: String?
    var errorMessage: String?
    var isLoading: Bool = false
}

struct AdminHubUi {
    var canWrite: Bool = false
    var metrics: [MetricUi] = []
    var sections: [AdminSectionRowUi] = []
    var loading: Bool = true
    var error: String?
}

struct AdminSectionRowUi: Identifiable {
    let id = UUID()
    let key: String
    let title: String
    let hint: String
}

struct AdminSectionUi {
    var title: String = ""
    var metrics: [MetricUi] = []
    var items: [ListCardUi] = []
    var shortcuts: [ActionUi] = []
    var summary: String = ""
    var loading: Bool = true
    var error: String?
}

enum ClientBookingTab {
    case active, completed
}

struct ClientDetailUi {
    var title: String = "Клиент"
    var name: String = ""
    var email: String = ""
    var phone: String = ""
    var bonusPoints: Int = 0
    var isBlocked: Bool = false
    var subscriptionTitle: String = ""
    var subscriptionMeta: String = ""
    var activeBookings: [ListCardUi] = []
    var completedBookings: [ListCardUi] = []
    var bookingTab: ClientBookingTab = .active
    var tickets: [ListCardUi] = []
    var loading: Bool = true
    var error: String?
    var showCallButton: Bool = false
}
