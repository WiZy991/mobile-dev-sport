package com.fitnessclub.app.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

const val NO_CONNECTION_MESSAGE = "Нет подключения к интернету. Проверьте сеть и попробуйте ещё раз."
const val SLOW_CONNECTION_MESSAGE = "Сервер долго не отвечает. Проверьте интернет и попробуйте ещё раз."
const val SECURE_CONNECTION_MESSAGE = "Не удалось установить защищённое соединение. Попробуйте позже."

private val CYRILLIC = Regex("[А-Яа-яЁё]")

fun networkErrorMessage(e: IOException): String = when (e) {
    is SocketTimeoutException -> SLOW_CONNECTION_MESSAGE
    is SSLException -> SECURE_CONNECTION_MESSAGE
    is UnknownHostException, is ConnectException -> NO_CONNECTION_MESSAGE
    else -> NO_CONNECTION_MESSAGE
}

/**
 * Текст ошибки для экрана: системные сообщения («Unable to resolve host…», «Expected BEGIN_OBJECT…»)
 * пользователю не показываем.
 */
fun Throwable.userMessage(fallback: String): String {
    val msg = message?.trim().orEmpty()
    if (msg.isNotEmpty() && CYRILLIC.containsMatchIn(msg) && !looksLikeDeveloperMessage(msg)) {
        return msg
    }
    val safeFallback = if (fallback == "Неизвестная ошибка") {
        "Не удалось выполнить запрос. Проверьте интернет и попробуйте ещё раз."
    } else {
        fallback
    }
    return if (this is IOException) networkErrorMessage(this) else safeFallback
}

private val TECH_MARKERS = listOf(
    "<html", "<!doctype", "nginx", "php-fpm", "cache:clear", "sqlstate",
    "stack trace", "whoops", "undefined index", "failed to open stream",
    "doctrine", "twig_", "vendor/", "fatal error", "parse error",
    "exception", "symfony\\", "zend", "file_get", "http 502", "http 503",
    "http 500", "http 404", "method not allowed",
)

fun looksLikeDeveloperMessage(text: String): Boolean {
    val t = text.lowercase()
    if (t.contains("http ") && Regex("http \\d{3}").containsMatchIn(t)) return true
    return TECH_MARKERS.any { t.contains(it) }
}

fun clientFacingMessage(text: String, http: Int?, fallback: String): String {
    val trimmed = text.trim()
    when (trimmed.lowercase()) {
        "unauthorized" -> return "Нужно войти в аккаунт ещё раз"
        "access denied" -> return "Аккаунт заблокирован. Обратитесь в клуб."
    }
    if (trimmed.isEmpty() || looksLikeDeveloperMessage(trimmed)) {
        return httpStatusFallback(http, fallback)
    }
    if (CYRILLIC.containsMatchIn(trimmed) && trimmed.length <= 280) {
        return trimmed
    }
    return httpStatusFallback(http, fallback)
}

fun httpStatusFallback(http: Int?, fallback: String): String = when {
    http == 401 -> "Нужно войти в аккаунт ещё раз"
    http == 403 -> "Недостаточно прав для этого действия."
    http == 429 -> "Слишком много попыток. Подождите немного и попробуйте снова."
    http == 404 || http == 405 -> fallback.ifBlank { "Не удалось выполнить запрос. Попробуйте позже." }
    http != null && http >= 500 -> fallback.ifBlank { "Сервис временно недоступен. Попробуйте позже." }
    else -> fallback.ifBlank { "Не удалось выполнить запрос. Попробуйте ещё раз." }
}

/** Сетевые исключения OkHttp → IOException с понятным русским текстом (его видят все экраны через e.message). */
class NetworkErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(chain.request())
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            throw IOException(networkErrorMessage(e), e)
        }
    }
}
