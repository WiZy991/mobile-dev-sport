import SwiftUI

enum SectionIcons {
    static func forSection(_ key: String) -> String {
        switch key {
        case "dashboard": return "square.grid.2x2"
        case "tasks": return "checklist"
        case "clients": return "person.2"
        case "schedule": return "calendar"
        case "bookings": return "calendar.badge.clock"
        case "subscriptions": return "figure.strengthtraining.traditional"
        case "visits": return "calendar.badge.clock"
        case "analytics": return "chart.bar"
        case "finance": return "creditcard"
        case "app_support": return "headphones"
        case "trainers": return "person.3"
        case "documents": return "folder"
        case "settings": return "gearshape"
        default: return "square.grid.2x2"
        }
    }
}
