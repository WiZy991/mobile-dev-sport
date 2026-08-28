package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.local.AuthFlowStore
import com.fitnessclub.app.data.local.BiometricLoginStore
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.model.ChangePasswordRequest
import com.fitnessclub.app.data.model.LoginHintRequest
import com.fitnessclub.app.data.model.LoginHintResponse
import com.fitnessclub.app.data.model.LoginHintResult
import com.fitnessclub.app.data.model.LoginRequest
import com.fitnessclub.app.data.model.ProfilePatchRequest
import com.fitnessclub.app.data.model.RegisterRequest
import com.fitnessclub.app.data.model.SberCallbackRequest
import com.fitnessclub.app.data.model.SberLoginRequest
import com.fitnessclub.app.data.model.User
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private data class ApiJsonError(
    val error: String? = null,
    val message: String? = null,
    val code: String? = null,
)

@Singleton
class AuthRepository @Inject constructor(
    private val api: FitnessApi,
    private val tokenManager: TokenManager,
    private val authFlowStore: AuthFlowStore,
    private val biometricLoginStore: BiometricLoginStore,
    private val gson: Gson,
) {

    fun getSberAuthorizeUrl(
        codeChallenge: String,
        redirectUri: String,
        appBridgeUri: String,
    ): Flow<ApiResult<String>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.sberLogin(
                SberLoginRequest(
                    codeChallenge = codeChallenge,
                    redirectUri = redirectUri,
                    appBridgeUri = appBridgeUri,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val url = response.body()!!.authorizeUrl.trim()
                if (url.isNotEmpty()) {
                    emit(ApiResult.Success(url))
                } else {
                    emit(ApiResult.Error("Сервер не вернул ссылку Сбер ID"))
                }
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Не удалось начать вход через Сбер ID"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    fun loginWithSberCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
        state: String,
        clubId: String? = null,
    ): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.sberCallback(
                SberCallbackRequest(
                    code = code,
                    codeVerifier = codeVerifier,
                    redirectUri = redirectUri,
                    state = state,
                    clubId = clubId?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                onAuthenticated()
                // Если бэкенд ещё не принял club_id — дотягиваем PUT /profile.
                val pendingClub = clubId?.trim()?.toIntOrNull()
                if (pendingClub != null && pendingClub > 0 && authResponse.user.clubId != pendingClub) {
                    when (val patched = setPreferredClub(pendingClub)) {
                        is ApiResult.Success -> emit(ApiResult.Success(patched.data))
                        else -> emit(ApiResult.Success(authResponse.user))
                    }
                } else {
                    emit(ApiResult.Success(authResponse.user))
                }
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Не удалось завершить вход через Сбер ID"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun login(email: String, password: String): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                onAuthenticated()
                emit(ApiResult.Success(authResponse.user))
            } else {
                val auth = parseAuthError(response, "Ошибка входа")
                emit(ApiResult.Error(auth.message, response.code(), auth.authCode))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    fun loginHint(email: String): Flow<ApiResult<LoginHintResult>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.loginHint(LoginHintRequest(email))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                emit(
                    ApiResult.Success(
                        LoginHintResult(
                            message = humanizeLoginHint(body.message, body.code),
                            code = body.code.trim(),
                        ),
                    ),
                )
            } else {
                val auth = parseAuthError(response, "Ошибка входа")
                if (auth.authCode == "password_not_set") {
                    emit(
                        ApiResult.Success(
                            LoginHintResult(passwordNotSetHintMessage(), "password_not_set"),
                        ),
                    )
                } else {
                    emit(ApiResult.Error(auth.message, response.code(), auth.authCode))
                }
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(loginPasswordRequiredMessage()))
        }
    }
    
    fun register(request: RegisterRequest): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.register(request)
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                onAuthenticated()
                emit(ApiResult.Success(authResponse.user))
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Ошибка регистрации"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    suspend fun logout() {
        // Если включён вход по отпечатку, НЕ отзываем сессию на сервере: биовход хранит
        // refresh-токен и должен работать после «Выйти». Иначе refresh обнуляется и
        // при входе по отпечатку сервер отвечает «Недействительный refresh-токен».
        val keepServerSessionForBiometric = biometricLoginStore.hasStoredCredential()
        if (!keepServerSessionForBiometric) {
            try {
                api.logout()
            } catch (_: Exception) {
                // Ignore logout errors
            }
        }
        // Не трогаем BiometricLoginStore: иначе после «Выйти» пропадает вход по отпечатку,
        // хотя пользователь явно включил его в настройках. Отключение — только из настроек.
        tokenManager.clearAll()
    }

    /**
     * Восстановление сессии по refresh-токену (после успешной биометрии).
     * Сохраняет новые токены и по возможности подтягивает профиль с сервера.
     */
    fun restoreSessionWithRefreshToken(refreshToken: String): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.refreshToken("Bearer ${refreshToken.trim()}")
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                // Чтобы getProfile() передал X-User-Id, сначала кладём пользователя из ответа refresh.
                tokenManager.saveUser(authResponse.user)
                val profileRes = runCatching { api.getProfile() }.getOrNull()
                val user = if (profileRes?.isSuccessful == true && profileRes.body() != null) {
                    profileRes.body()!!
                } else {
                    authResponse.user
                }
                tokenManager.saveUser(user)
                emit(ApiResult.Success(user))
            } else {
                val parsed = parseAuthError(response, "Сессия устарела")
                emit(ApiResult.Error(parsed.message, response.code(), parsed.authCode))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    /** Синхронное восстановление сессии (старт приложения / биометрия). */
    suspend fun restoreSessionOnce(refreshToken: String): ApiResult<User> {
        return try {
            val response = api.refreshToken("Bearer ${refreshToken.trim()}")
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                val profileRes = runCatching { api.getProfile() }.getOrNull()
                val user = if (profileRes?.isSuccessful == true && profileRes.body() != null) {
                    profileRes.body()!!
                } else {
                    authResponse.user
                }
                tokenManager.saveUser(user)
                ApiResult.Success(user)
            } else {
                val parsed = parseAuthError(response, "Сессия устарела")
                ApiResult.Error(parsed.message, response.code(), parsed.authCode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    fun isLoggedIn(): Flow<Boolean> = tokenManager.isLoggedIn()

    /**
     * Старт приложения: подтянуть сессию с диска и обновить access через refresh.
     * Невалидный refresh → очистка (обычный логин). Сетевой сбой при живом access → остаёмся в аккаунте.
     */
    suspend fun bootstrapSession() {
        tokenManager.awaitHydrated()
        val refresh = tokenManager.getRefreshToken()?.trim().orEmpty()
        if (refresh.isEmpty()) {
            return
        }
        when (val result = restoreSessionOnce(refresh)) {
            is ApiResult.Success -> Unit
            is ApiResult.Error -> {
                val unauthorized = result.code == 401 ||
                    result.authCode == "invalid_refresh" ||
                    result.authCode == "token_expired" ||
                    result.authCode == "invalid_token"
                if (unauthorized) {
                    tokenManager.clearAll()
                    return
                }
                // Офлайн / 5xx: если access уже в памяти с диска — считаем залогиненным.
                if (tokenManager.peekAccessToken().isNullOrBlank()) {
                    tokenManager.clearAll()
                }
            }
            is ApiResult.Loading -> Unit
        }
    }

    fun hasCompletedRegistration(): Flow<Boolean> = authFlowStore.hasCompletedRegistration
    
    fun getCurrentUser(): Flow<User?> = tokenManager.getUser()

    /** Подтягивает свежий профиль с сервера и обновляет кэш (например, club_name). */
    suspend fun refreshCurrentUser(): User? {
        val profileRes = runCatching { api.getProfile() }.getOrNull()
        val user = profileRes?.takeIf { it.isSuccessful }?.body() ?: return null
        tokenManager.saveUser(user)
        return user
    }

    /** Обновление профиля (PUT /user/profile). Возвращает актуального пользователя и кэширует его. */
    suspend fun updateProfile(
        name: String,
        email: String,
        phone: String,
        dateOfBirth: String?,
        passportSeries: String? = null,
        passportNumber: String? = null,
        passportIssuedBy: String? = null,
        passportIssueDate: String? = null,
        registrationAddress: String? = null,
    ): ApiResult<User> {
        val current = tokenManager.getUser().first()
            ?: return ApiResult.Error("Профиль не загружен")
        val payload = current.copy(
            name = name.trim(),
            email = email.trim(),
            phone = phone.trim(),
            dateOfBirth = dateOfBirth?.trim()?.takeIf { it.isNotEmpty() },
            passportSeries = passportSeries?.trim()?.takeIf { it.isNotEmpty() } ?: current.passportSeries,
            passportNumber = passportNumber?.trim()?.takeIf { it.isNotEmpty() } ?: current.passportNumber,
            passportIssuedBy = passportIssuedBy?.trim()?.takeIf { it.isNotEmpty() } ?: current.passportIssuedBy,
            passportIssueDate = passportIssueDate?.trim()?.takeIf { it.isNotEmpty() } ?: current.passportIssueDate,
            registrationAddress = registrationAddress?.trim()?.takeIf { it.isNotEmpty() }
                ?: current.registrationAddress,
        )
        return try {
            val response = api.updateProfile(payload)
            if (response.isSuccessful && response.body() != null) {
                val updated = response.body()!!
                tokenManager.saveUser(updated)
                ApiResult.Success(updated)
            } else {
                ApiResult.Error(authErrorMessage(response, "Не удалось сохранить профиль"), response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    /** Сохранить паспорт перед покупкой абонемента. */
    suspend fun savePassportForPurchase(
        series: String,
        number: String,
        issuedBy: String,
        issueDateIso: String,
        registrationAddress: String,
        dateOfBirthIso: String? = null,
    ): ApiResult<User> {
        val current = tokenManager.getUser().first()
            ?: return ApiResult.Error("Профиль не загружен")
        return updateProfile(
            name = current.name,
            email = current.email,
            phone = current.phone,
            dateOfBirth = dateOfBirthIso ?: current.dateOfBirth,
            passportSeries = series,
            passportNumber = number,
            passportIssuedBy = issuedBy,
            passportIssueDate = issueDateIso,
            registrationAddress = registrationAddress,
        )
    }

    /** Привязать зал клиента (выбор при регистрации / смене клуба). */
    suspend fun setPreferredClub(clubId: Int): ApiResult<User> {
        if (clubId <= 0) {
            return ApiResult.Error("Некорректный клуб")
        }
        // Только club_id: полный User тащит паспорт → 403 passport_locked после Сбер ID.
        return try {
            val response = api.patchProfile(ProfilePatchRequest(clubId = clubId))
            if (response.isSuccessful && response.body() != null) {
                val updated = response.body()!!
                tokenManager.saveUser(updated)
                ApiResult.Success(updated)
            } else {
                ApiResult.Error(authErrorMessage(response, "Не удалось сохранить клуб"), response.code())
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    suspend fun getAccessToken(): String? = tokenManager.getAccessToken()

    fun changePassword(
        currentPassword: String,
        newPassword: String,
    ): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                )
            )
            if (response.isSuccessful) {
                emit(ApiResult.Success(Unit))
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Не удалось сменить пароль"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }

    private data class ParsedAuthError(
        val message: String,
        val authCode: String?,
    )

    private fun parseApiJsonError(response: Response<*>): ApiJsonError? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        return runCatching { gson.fromJson(raw, ApiJsonError::class.java) }.getOrNull()
    }

    private fun parseAuthError(response: Response<*>, fallback: String): ParsedAuthError {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, ApiJsonError::class.java) }.getOrNull()
        val server = parsed?.error?.takeIf { it.isNotBlank() }
            ?: parsed?.message?.takeIf { it.isNotBlank() }
        val trimmed = raw.trim()
        val plain = trimmed.takeIf {
            it.isNotEmpty() && !it.startsWith("{") && !it.startsWith("[") && !it.startsWith("<")
        }
        val base = server ?: plain ?: fallback
        val authCode = parsed?.code?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedAuthError(
            message = humanizeKnownAuthMessages(base, authCode),
            authCode = authCode,
        )
    }

    private fun authErrorMessage(response: Response<*>, fallback: String): String =
        parseAuthError(response, fallback).message

    private fun humanizeKnownAuthMessages(text: String, code: String?): String {
        when (code?.trim()) {
            "password_not_set" -> return passwordNotSetHintMessage()
            "passport_locked" -> return "Паспорт подтверждён через Сбер ID и не может быть изменён из приложения"
        }
        return when (text.trim()) {
        "Укажите email и password", "Введите пароль" -> loginPasswordRequiredMessage()
        "Для этого аккаунта вход по паролю не настроен",
        "Аккаунт найден. Вы регистрировались через Сбер ID — пароль не задан. Войдите через Сбер ID, затем в приложении: Профиль → Изменить пароль." ->
            passwordNotSetHintMessage()
        "Этот email уже привязан к аккаунту Сбер ID. Войдите через Сбер ID и задайте пароль в Профиле → Изменить пароль." ->
            passwordNotSetHintMessage()
        "Пароль должен быть не менее 6 символов" -> "Пароль должен быть не менее 6 символов"
        "Некорректный email" -> "Некорректный email"
        "User with this email already exists",
        "Пользователь с таким email уже существует",
        "Пользователь с таким email уже зарегистрирован" ->
            "Этот email уже зарегистрирован. Войдите в аккаунт или укажите другой адрес."
        "Email is required", "Укажите email" -> "Укажите email"
        "Неверный текущий пароль" -> "Неверный текущий пароль"
        "Новый пароль должен отличаться от текущего" -> "Новый пароль должен отличаться от текущего"
        "User not found" -> "Неверный email или пароль"
        "Access denied" -> "Доступ запрещён"
        else -> {
            if (text.contains("Method Not Allowed", ignoreCase = true)) {
                "Ошибка соединения с сервером. Обновите приложение или проверьте интернет."
            } else {
                text
            }
        }
        }
    }

    private fun humanizeLoginHint(message: String, code: String): String = when (code.trim()) {
        "password_not_set" -> passwordNotSetHintMessage()
        "password_required", "missing_password" -> loginPasswordRequiredMessage()
        "email_unknown" -> message.ifBlank {
            "Введите пароль. Если аккаунта ещё нет — пройдите регистрацию."
        }
        else -> message.ifBlank { loginPasswordRequiredMessage() }
    }

    private suspend fun onAuthenticated() {
        authFlowStore.markRegistrationCompleted()
        authFlowStore.clearPendingSberVerifier()
        val user = tokenManager.getUser().first()
        if (user != null) {
            biometricLoginStore.onAuthenticated(user.id)
        }
    }
}

internal fun passwordNotSetHintMessage(): String =
    "Аккаунт с этим email уже есть в клубе, но пароль ещё не задан.\n\n" +
        "Пройдите регистрацию с этим email и придумайте пароль — вход откроется сразу."

internal fun loginPasswordRequiredMessage(): String = "Введите пароль"
