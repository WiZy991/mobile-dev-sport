package com.example.staffapp

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UserFacingError {
    fun message(error: Throwable): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()

        return when {
            error is SocketTimeoutException -> "Сервер долго отвечает. Повторите попытку."
            error is UnknownHostException || error is ConnectException ->
                "Нет связи с CRM. Запустите backend и проверьте адрес API в strings.xml."
            lower.contains("401") || lower.contains("unauthorized") ->
                "Сессия истекла. Выйдите и войдите заново."
            lower.contains("http 403") || lower.contains("403") || lower.contains("forbidden") ->
                "У вас нет прав для этого действия."
            lower.contains("no route found") || Regex("""\b404\b""").containsMatchIn(lower) ->
                "На сервере нет нужного API (404). Обновите CRM и перезапустите контейнер."
            lower.contains("http 500") || lower.contains("500") || lower.contains("internal server error") ->
                "Ошибка сервера CRM. Уже разбираемся, попробуйте позже."
            lower.contains("could not find driver") ->
                "Сервер CRM настроен некорректно. Обратитесь к администратору."
            lower.contains("<!doctype html") || lower.contains("<html") || lower.contains("html response") ->
                "Сервер вернул техническую ошибку. Проверьте, что backend запущен."
            lower.contains("json parse") || lower.contains("empty response") ->
                "Не удалось прочитать ответ CRM. Запустите backend:\ncd crm-backend-symfony\nphp -S 0.0.0.0:8000 -t public public/index.php"
            raw.startsWith("HTTP ") ->
                "Ошибка CRM: $raw"
            raw.isBlank() -> "Не удалось выполнить запрос. Повторите попытку."
            Regex("\\p{IsCyrillic}").containsMatchIn(raw) -> raw
            else -> "Не удалось загрузить данные. Повторите попытку."
        }
    }
}
