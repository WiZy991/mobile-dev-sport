package com.example.staffapp

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object UserFacingError {
    fun message(error: Throwable): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        val detail = httpDetail(raw)

        return when {
            error is SocketTimeoutException -> "Сервер долго отвечает. Повторите попытку."
            error is UnknownHostException || error is ConnectException ->
                "Нет связи с CRM. Запустите backend и проверьте адрес API в strings.xml."

            // Ошибки входа/регистрации (тоже HTTP 401) — не путать с истекшей сессией.
            // Всегда одна понятная фраза (в т.ч. вместо старого «Неверные данные» с сервера).
            isAuthCredentialsFailure(lower, detail) ->
                "Неверный email или пароль"

            lower.contains("registration_rejected") ||
                detail.contains("отклон", ignoreCase = true) ->
                "Регистрация отклонена администратором"

            lower.contains("invalid_refresh") || lower.contains("missing_refresh") ->
                "Сессия истекла. Выйдите и войдите заново."

            // 401 без текста про логин — реальная просроченная сессия на рабочих API.
            lower.contains("401") || lower.contains("unauthorized") -> {
                val clean = stripApiCode(detail)
                when {
                    isVagueCredentialsText(clean) -> "Неверный email или пароль"
                    hasCyrillic(clean) && !looksLikeSessionExpiry(clean) -> clean
                    else -> "Сессия истекла. Выйдите и войдите заново."
                }
            }

            lower.contains("http 403") || lower.contains("403") || lower.contains("forbidden") ->
                "У вас нет прав для этого действия."

            // Реальный «маршрута нет» — только No route found / Symfony. Не путать с club_not_found.
            lower.contains("no route found") || lower.contains("cannot find the \"") ->
                "На CRM ещё нет этого API. Задеплойте свежий crm-backend-symfony (migrate + перезапуск контейнера)."

            // Любой HTTP с русским текстом ошибки с сервера — показываем его (в т.ч. 404 club_not_found).
            raw.startsWith("HTTP ") -> {
                val clean = stripApiCode(detail)
                when {
                    hasCyrillic(clean) -> clean
                    Regex("""\b404\b""").containsMatchIn(lower) ->
                        "На CRM ещё нет этого API. Задеплойте свежий crm-backend-symfony (migrate + перезапуск контейнера)."
                    else -> "Ошибка CRM: $raw"
                }
            }

            Regex("""\b404\b""").containsMatchIn(lower) ->
                "На CRM ещё нет этого API. Задеплойте свежий crm-backend-symfony (migrate + перезапуск контейнера)."
            lower.contains("http 500") || lower.contains("500") || lower.contains("internal server error") ->
                "Ошибка сервера CRM. Уже разбираемся, попробуйте позже."
            lower.contains("could not find driver") ->
                "Сервер CRM настроен некорректно. Обратитесь к администратору."
            lower.contains("<!doctype html") || lower.contains("<html") || lower.contains("html response") ->
                "Сервер вернул техническую ошибку. Проверьте, что backend запущен."
            lower.contains("json parse") || lower.contains("empty response") ->
                "Не удалось прочитать ответ CRM. Запустите backend:\ncd crm-backend-symfony\nphp -S 0.0.0.0:8000 -t public public/index.php"
            raw.isBlank() -> "Не удалось выполнить запрос. Повторите попытку."
            hasCyrillic(raw) -> raw
            else -> "Не удалось загрузить данные. Повторите попытку."
        }
    }

    private fun httpDetail(raw: String): String =
        if (raw.startsWith("HTTP ")) raw.substringAfter(": ", "").trim() else raw.trim()

    private fun stripApiCode(detail: String): String =
        detail.replace(Regex("""\s*\[[a-z0-9_]+]\s*$""", RegexOption.IGNORE_CASE), "").trim()

    private fun hasCyrillic(text: String): Boolean =
        Regex("\\p{IsCyrillic}").containsMatchIn(text)

    private fun looksLikeSessionExpiry(detail: String): Boolean {
        val d = detail.lowercase()
        return d.contains("сессия") || d.contains("refresh") || d.contains("unauthorized")
    }

    private fun isAuthCredentialsFailure(lower: String, detail: String): Boolean {
        if (lower.contains("invalid_credentials") || lower.contains("missing_credentials")) {
            return true
        }
        return isVagueCredentialsText(stripApiCode(detail))
    }

    /** Старые ответы CRM вроде «Неверные данные». */
    private fun isVagueCredentialsText(detail: String): Boolean {
        val d = detail.lowercase().trim()
        if (d == "неверные данные" || d.startsWith("неверные данные")) return true
        return d.contains("неверн") &&
            (d.contains("парол") || d.contains("email") || d.contains("e-mail") ||
                d.contains("данн") || d.contains("логин"))
    }
}
