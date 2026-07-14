package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.model.ChangePasswordRequest
import com.fitnessclub.app.data.model.LoginRequest
import com.fitnessclub.app.data.model.RegisterRequest
import com.fitnessclub.app.data.model.SberCallbackRequest
import com.fitnessclub.app.data.model.SberLoginRequest
import com.fitnessclub.app.data.model.User
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private data class ApiJsonError(
    val error: String? = null,
    val message: String? = null,
)

@Singleton
class AuthRepository @Inject constructor(
    private val api: FitnessApi,
    private val tokenManager: TokenManager,
    private val gson: Gson,
) {

    fun getSberAuthorizeUrl(
        codeChallenge: String,
        redirectUri: String,
    ): Flow<ApiResult<String>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.sberLogin(
                SberLoginRequest(
                    codeChallenge = codeChallenge,
                    redirectUri = redirectUri,
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
    ): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.sberCallback(
                SberCallbackRequest(
                    code = code,
                    codeVerifier = codeVerifier,
                    redirectUri = redirectUri,
                    state = state,
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                emit(ApiResult.Success(authResponse.user))
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
                emit(ApiResult.Success(authResponse.user))
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Ошибка входа"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
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
                emit(ApiResult.Success(authResponse.user))
            } else {
                emit(ApiResult.Error(authErrorMessage(response, "Ошибка регистрации"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
            // Ignore logout errors
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
                emit(ApiResult.Error(authErrorMessage(response, "Сессия устарела"), response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun isLoggedIn(): Flow<Boolean> = tokenManager.isLoggedIn()
    
    fun getCurrentUser(): Flow<User?> = tokenManager.getUser()
    
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

    private fun authErrorMessage(response: Response<*>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = runCatching { gson.fromJson(raw, ApiJsonError::class.java) }.getOrNull()
        val server = parsed?.error?.takeIf { it.isNotBlank() }
            ?: parsed?.message?.takeIf { it.isNotBlank() }
        val trimmed = raw.trim()
        val plain = trimmed.takeIf {
            it.isNotEmpty() && !it.startsWith("{") && !it.startsWith("[") && !it.startsWith("<")
        }
        val base = server ?: plain ?: fallback
        return humanizeKnownAuthMessages(base)
    }

    private fun humanizeKnownAuthMessages(text: String): String = when {
        text.contains("Method Not Allowed", ignoreCase = true) ->
            "Ошибка соединения с сервером. Обновите приложение или проверьте интернет."
        else -> when (text.trim()) {
        "Укажите email и password" -> "Введите email и пароль"
        "Для этого аккаунта вход по паролю не настроен" ->
            "Для этого аккаунта вход по паролю не настроен. Войдите через Сбер ID или зарегистрируйтесь заново."
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
        else -> text
        }
    }
}
