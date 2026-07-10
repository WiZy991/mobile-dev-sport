import Foundation

enum SectionHints {
    static func forSection(_ key: String) -> String {
        switch key {
        case "dashboard": return "Сводка, задачи, лиды, записи"
        case "clients": return "Список клиентов и карточки"
        case "schedule": return "Тренировки и календарь"
        case "bookings": return "Записи на занятия"
        case "leads": return "Воронка и новые лиды"
        case "app_support": return "Обращения из приложения"
        case "finance": return "Доходы и расходы"
        case "crm_staff": return "Сотрудники CRM"
        case "tasks": return "Задачи и поручения"
        case "subscriptions": return "Абонементы клиентов"
        default: return "Данные раздела CRM"
        }
    }
}
