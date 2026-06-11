package com.example.staffapp.ui.work

object SectionHints {
    fun forSection(key: String): String = when (key) {
        "dashboard" -> "Сводка, задачи, лиды, записи"
        "clients" -> "Список клиентов и карточки"
        "schedule" -> "Тренировки и календарь"
        "bookings" -> "Записи на занятия"
        "leads" -> "Воронка и новые лиды"
        "app_support" -> "Обращения из приложения"
        "finance" -> "Доходы и расходы"
        "crm_staff" -> "Сотрудники CRM"
        "tasks" -> "Задачи и поручения"
        "subscriptions" -> "Абонементы клиентов"
        else -> "Данные раздела CRM"
    }
}
