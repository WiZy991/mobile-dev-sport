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
    var refType: String?
    var feedId: Int?

    var isClickable: Bool {
        clientId != nil || ticketId != nil || refType == "client" || refType == "ticket"
    }
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
}

struct ScheduleTabUi {
    var days: [ScheduleDayUi] = []
    var sessions: [ScheduleSessionUi] = []
    var selectedTypeFilter: String?
    var denied: Bool = false
    var deniedMessage: String = ""
    var loading: Bool = false
}

struct ClientsTabUi {
    var query: String = ""
    var summary: String = ""
    var items: [ListCardUi] = []
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
    var sections: [ProfileSectionUi] = []
    var adminAvailable: Bool = false
    var showAdminButton: Bool = false
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

struct ClientDetailUi {
    var title: String = "Клиент"
    var name: String = ""
    var email: String = ""
    var phone: String = ""
    var bonusPoints: Int = 0
    var isBlocked: Bool = false
    var subscriptionTitle: String = ""
    var subscriptionMeta: String = ""
    var bookings: [ListCardUi] = []
    var tickets: [ListCardUi] = []
    var loading: Bool = true
    var error: String?
    var showCallButton: Bool = false
}
