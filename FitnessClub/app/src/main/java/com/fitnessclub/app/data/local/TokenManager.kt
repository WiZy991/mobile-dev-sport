package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.fitnessclub.app.data.model.User
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** Старый файл DataStore — один раз очищаем при старте, чтобы не подтягивать прошлую сессию. */
private val Context.legacyFitnessPrefs by preferencesDataStore(name = "fitness_prefs")

/**
 * Токены и пользователь только в памяти процесса: после полного закрытия приложения
 * снова нужен вход (логин/пароль или биометрия, если настроена).
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    private val accessToken = MutableStateFlow<String?>(null)
    private val refreshToken = MutableStateFlow<String?>(null)
    private val userJson = MutableStateFlow<String?>(null)

    init {
        runBlocking {
            runCatching { context.legacyFitnessPrefs.edit { it.clear() } }
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken.value = accessToken
        this.refreshToken.value = refreshToken
    }

    suspend fun saveUser(user: User) {
        userJson.value = gson.toJson(user)
    }

    suspend fun getAccessToken(): String? = accessToken.value

    suspend fun getRefreshToken(): String? = refreshToken.value

    fun getUser(): Flow<User?> {
        return userJson.map { json ->
            json?.let {
                try {
                    gson.fromJson(it, User::class.java)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    fun isLoggedIn(): Flow<Boolean> {
        return accessToken.map { !it.isNullOrEmpty() }
    }

    suspend fun clearAll() {
        accessToken.value = null
        refreshToken.value = null
        userJson.value = null
        runCatching { context.legacyFitnessPrefs.edit { it.clear() } }
    }
}
