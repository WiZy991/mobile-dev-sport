import Foundation

enum UiLabels {
    private static let roleMap: [String: String] = [
        "ROLE_TRAINER": "Тренер",
        "ROLE_MANAGER": "Менеджер",
        "ROLE_FINANCE": "Финансы",
        "ROLE_VIEWER": "Наблюдатель",
        "ROLE_SUPPORT": "Поддержка",
        "ROLE_ADMIN": "Администратор",
        "ROLE_SUPER_ADMIN": "Суперадминистратор",
    ]

    private static let sectionMap: [String: String] = [
        "home": "Главная",
        "profile": "Профиль",
        "dashboard": "Дашборд",
        "tasks": "Задачи",
        "clients": "Клиенты",
        "schedule": "Расписание",
        "bookings": "Записи",
        "subscriptions": "Абонементы",
        "visits": "Посещения",
        "analytics": "Аналитика",
        "finance": "Финансы",
        "trainers": "Тренеры",
        "documents": "Документы",
        "crm_staff": "Персонал CRM",
        "app_support": "Обращения",
        "leads": "Лиды",
        "deals": "Сделки",
        "sales": "Продажи",
        "services": "Услуги",
        "promocodes": "Промокоды",
        "promotions": "Акции",
        "tags": "Теги",
        "comments": "Комментарии",
        "settings": "Настройки",
        "warehouse": "Склад",
        "cashdesk": "Касса",
        "mobileapps": "Мобильные приложения",
        "messengers": "Мессенджеры",
        "calls": "Звонки",
        "selfservice": "Самообслуживание",
        "franchise": "Франшиза",
    ]

    static func sectionTitle(_ key: String) -> String {
        sectionMap[key] ?? key.prefix(1).uppercased() + key.dropFirst()
    }

    static func roleTitle(_ code: String) -> String {
        roleMap[code] ?? code
    }

    static func metricTitle(_ key: String) -> String {
        switch key {
        case "clients": return "Клиенты"
        case "bookings": return "Записи"
        case "subscriptions": return "Абонементы"
        case "tasks_open": return "Открытые задачи"
        case "tasks_done": return "Закрытые задачи"
        case "all_clients": return "Всего клиентов"
        case "all_bookings": return "Всего записей"
        case "all_subscriptions": return "Всего абонементов"
        case "trainers_total": return "Всего тренеров"
        case "documents_total": return "Всего документов"
        case "staff_total": return "Всего сотрудников"
        case "trainers": return "Тренеры"
        case "documents": return "Документы"
        case "staff": return "Сотрудники"
        case "schedule_today": return "Тренировок сегодня"
        case "schedule_tomorrow": return "Тренировок завтра"
        case "schedule_week": return "Тренировок на этой неделе"
        case "schedule_next_week": return "Тренировок на следующей неделе"
        case "items_total": return "Элементов"
        case "support_new": return "Новых обращений"
        case "notifications_unread": return "Непрочитанных уведомлений"
        case "leads_new": return "Новых лидов"
        case "leads_converted": return "Конвертированных лидов"
        case "deals_total": return "Сделок"
        case "sales_total": return "Продаж"
        case "visits_today": return "Проходов сегодня"
        case "finance_income_month": return "Доход за месяц"
        case "finance_expense_month": return "Расход за месяц"
        case "products_total": return "Услуг в каталоге"
        case "promocodes_total": return "Промокодов"
        case "promotions_total": return "Акций"
        case "tags_total": return "Тегов"
        case "comments_total": return "Комментариев"
        default: return key
        }
    }

    static func ticketCategory(_ category: String) -> String {
        switch category {
        case "question": return "Вопрос"
        case "complaint": return "Жалоба"
        case "suggestion": return "Предложение"
        case "technical": return "Техническая проблема"
        case "billing": return "Оплата / абонемент"
        default: return "Другое"
        }
    }

    static func ticketStatus(_ status: String) -> String {
        switch status {
        case "new": return "Новое"
        case "in_progress": return "В работе"
        case "done": return "Закрыто"
        default: return status
        }
    }

    static func trainingType(_ type: String) -> String {
        switch type {
        case "personal": return "Персональная"
        case "group": return "Групповая"
        case "extra": return "Услуга"
        default: return "Занятие"
        }
    }
}
