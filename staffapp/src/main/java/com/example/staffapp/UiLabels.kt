package com.example.staffapp

object UiLabels {
    private val roleMap = mapOf(
        "ROLE_TRAINER" to "Тренер",
        "ROLE_MANAGER" to "Менеджер",
        "ROLE_FINANCE" to "Финансы",
        "ROLE_VIEWER" to "Наблюдатель",
        "ROLE_SUPPORT" to "Поддержка",
        "ROLE_ADMIN" to "Администратор",
        "ROLE_SUPER_ADMIN" to "Суперадминистратор",
    )

    private val sectionMap = mapOf(
        "home" to "Главная",
        "profile" to "Профиль",
        "dashboard" to "Дашборд",
        "tasks" to "Задачи",
        "clients" to "Клиенты",
        "schedule" to "Расписание",
        "bookings" to "Записи",
        "subscriptions" to "Абонементы",
        "visits" to "Посещения",
        "analytics" to "Аналитика",
        "finance" to "Финансы",
        "trainers" to "Тренеры",
        "documents" to "Документы",
        "crm_staff" to "Персонал CRM",
        "app_support" to "Обращения",
        "leads" to "Лиды",
        "deals" to "Сделки",
        "sales" to "Продажи",
        "services" to "Услуги",
        "visits" to "Посещения",
        "analytics" to "Аналитика",
        "promocodes" to "Промокоды",
        "promotions" to "Акции",
        "tags" to "Теги",
        "comments" to "Комментарии",
        "settings" to "Настройки",
        "warehouse" to "Склад",
        "cashdesk" to "Касса",
        "mobileapps" to "Мобильные приложения",
        "messengers" to "Мессенджеры",
        "calls" to "Звонки",
        "selfservice" to "Самообслуживание",
        "franchise" to "Франшиза",
    )

    fun sectionTitle(key: String): String = sectionMap[key] ?: key.replaceFirstChar { it.uppercase() }
    fun roleTitle(code: String): String = roleMap[code] ?: code

    fun metricTitle(key: String): String = when (key) {
        "clients" -> "Клиенты"
        "bookings" -> "Записи"
        "subscriptions" -> "Абонементы"
        "tasks_open" -> "Открытые задачи"
        "tasks_done" -> "Закрытые задачи"
        "all_clients" -> "Всего клиентов"
        "all_bookings" -> "Всего записей"
        "all_subscriptions" -> "Всего абонементов"
        "trainers_total" -> "Всего тренеров"
        "documents_total" -> "Всего документов"
        "staff_total" -> "Всего сотрудников"
        "trainers" -> "Тренеры"
        "documents" -> "Документы"
        "staff" -> "Сотрудники"
        "schedule_today" -> "Тренировок сегодня"
        "schedule_tomorrow" -> "Тренировок завтра"
        "schedule_week" -> "Тренировок на этой неделе"
        "schedule_next_week" -> "Тренировок на следующей неделе"
        "items_total" -> "Элементов"
        "support_new" -> "Новых обращений"
        "notifications_unread" -> "Непрочитанных уведомлений"
        "leads_new" -> "Новых лидов"
        "leads_converted" -> "Конвертированных лидов"
        "deals_total" -> "Сделок"
        "sales_total" -> "Продаж"
        "visits_today" -> "Проходов сегодня"
        "finance_income_month" -> "Доход за месяц"
        "finance_expense_month" -> "Расход за месяц"
        "products_total" -> "Услуг в каталоге"
        "promocodes_total" -> "Промокодов"
        "promotions_total" -> "Акций"
        "tags_total" -> "Тегов"
        "comments_total" -> "Комментариев"
        else -> key
    }

    fun ticketCategory(category: String): String = when (category) {
        "question" -> "Вопрос"
        "complaint" -> "Жалоба"
        "suggestion" -> "Предложение"
        "technical" -> "Техническая проблема"
        "billing" -> "Оплата / абонемент"
        else -> "Другое"
    }

    fun ticketStatus(status: String): String = when (status) {
        "new" -> "Новое"
        "in_progress" -> "В работе"
        "done" -> "Закрыто"
        else -> status
    }

    fun trainingType(type: String): String = when (type) {
        "personal" -> "Персональная"
        "group" -> "Групповая"
        "extra" -> "Услуга"
        else -> "Занятие"
    }
}
