import Foundation

struct StaffSession: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    let userEmail: String
}

struct RoleConfig: Codable, Equatable {
    let roles: [String]
    let appSections: [String]
    let adminSections: [String]
    let adminActions: [String]
    let featureFlags: [String: Bool]
}

struct StaffAppData {
    let employeeName: String
    let employeeEmail: String
    let sections: [String]
    let metrics: [String: Int]
}

struct StaffAdminData {
    let adminSections: [String]
    let adminMenu: [String: String]
    let widgets: [String: Int]
    let canWrite: Bool
}

struct SectionData {
    let mode: String
    let section: String
    let cards: [String: Int]
}

struct ScheduleDay {
    let date: String
    let label: String
    let count: Int
}

struct ScheduleItem {
    let title: String
    let trainer: String
    let type: String
    let date: String
    let dayLabel: String
    let startTime: String
    let endTime: String
    let startAt: String
    let endAt: String
    let room: String
    let clientNames: [String]
    let participants: String
}

struct ScheduleData {
    let days: [ScheduleDay]
    let items: [ScheduleItem]
}

struct FeedListItem {
    let title: String
    let subtitle: String
    let meta: String
    let id: Int?
    let refType: String?
}

struct SupportTicketItem {
    let id: Int
    let subject: String
    let message: String
    let category: String
    let status: String
    let contactEmail: String
    let clientName: String
    let clientPhone: String
    let clientId: Int?
    let createdAt: String
}

struct ClientSummary {
    let id: Int
    let name: String
    let email: String
    let phone: String
}

struct ClientSubscription {
    let plan: String
    let status: String
    let endDate: String?
    let visitsUsed: Int
    let visitsTotal: Int
}

struct ClientDetailRow {
    let title: String
    let meta: String
}

struct ClientDetail {
    let id: Int
    let name: String
    let email: String
    let phone: String
    let bonusPoints: Int
    let isBlocked: Bool
    let subscription: ClientSubscription?
    let recentBookings: [ClientDetailRow]
    let recentTickets: [ClientDetailRow]
}

struct SupportTicketsData {
    let items: [SupportTicketItem]
    let newCount: Int
}

struct StaffNotificationItem {
    let id: Int
    let type: String
    let title: String
    let body: String
    let referenceId: String
    let createdAt: String
    let isRead: Bool
}

struct StaffNotificationsData {
    let items: [StaffNotificationItem]
    let unreadCount: Int
}
