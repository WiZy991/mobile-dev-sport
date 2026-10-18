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
    if (msg.isNotEmpty() && CYRILLIC.containsMatchIn(msg)) return msg
    return if (this is IOException) networkErrorMessage(this) else fallback
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
