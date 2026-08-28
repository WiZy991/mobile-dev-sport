package com.fitnessclub.app.data.auth

import android.util.Log
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.model.AuthResponse
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access живёт ~1 час. Пока приложение открыто, на 401 тихо обновляем пару через refresh
 * и повторяем запрос — без ошибки «Unauthorized» и без повторного логина.
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val gson: Gson,
) : Authenticator {

    private val refreshLock = Any()

    /** Отдельный клиент без authenticator — иначе рекурсия на /auth/refresh. */
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val path = response.request.url.encodedPath
        if (path.contains("/auth/refresh") ||
            path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/sber")
        ) {
            return null
        }

        val failedAccess = bearerOf(response.request.header("Authorization"))

        synchronized(refreshLock) {
            val currentAccess = tokenManager.peekAccessToken()
            // Параллельный запрос уже успел обновить access — просто повторяем с новым.
            if (!currentAccess.isNullOrBlank() &&
                !failedAccess.isNullOrBlank() &&
                currentAccess != failedAccess
            ) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccess")
                    .build()
            }

            val refresh = tokenManager.peekRefreshToken()?.trim().orEmpty()
            if (refresh.isEmpty()) {
                return null
            }

            val issued = refreshAccess(refresh)
            return when {
                issued.auth != null -> {
                    tokenManager.applyRefreshedSession(
                        accessToken = issued.auth.token,
                        refreshToken = issued.auth.refreshToken,
                        user = issued.auth.user,
                    )
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${issued.auth.token}")
                        .build()
                }
                issued.invalidateSession -> {
                    Log.w(TAG, "Refresh отклонён сервером — нужен повторный вход")
                    tokenManager.clearSessionAsync()
                    null
                }
                else -> {
                    Log.w(TAG, "Refresh временно недоступен — оставляем сессию")
                    null
                }
            }
        }
    }

    private data class RefreshAttempt(
        val auth: AuthResponse? = null,
        val invalidateSession: Boolean = false,
    )

    private fun refreshAccess(refreshToken: String): RefreshAttempt {
        val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        val url = base + "auth/refresh"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody(null))
            .header("Authorization", "Bearer $refreshToken")
            .header("Accept", "application/json")

        val orgSlug = BuildConfig.ORGANIZATION_SLUG.trim()
        if (orgSlug.isNotEmpty()) {
            requestBuilder.header("X-Organization-Slug", orgSlug)
        }
        requestBuilder.header("X-App-Deep-Link-Scheme", BuildConfig.DEEP_LINK_SCHEME)

        return try {
            refreshClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "auth/refresh HTTP ${resp.code}")
                    return RefreshAttempt(invalidateSession = resp.code == 401 || resp.code == 403)
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return RefreshAttempt(invalidateSession = false)
                }
                val parsed = gson.fromJson(body, AuthResponse::class.java)
                    ?.takeIf { it.token.isNotBlank() && it.refreshToken.isNotBlank() }
                if (parsed == null) {
                    RefreshAttempt(invalidateSession = false)
                } else {
                    RefreshAttempt(auth = parsed)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "auth/refresh failed: ${e.message}")
            RefreshAttempt(invalidateSession = false)
        }
    }

    private fun bearerOf(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed.substring(7).trim().takeIf { it.isNotEmpty() }
        } else {
            trimmed.takeIf { it.isNotEmpty() }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val TAG = "FC_AuthRefresh"
    }
}
