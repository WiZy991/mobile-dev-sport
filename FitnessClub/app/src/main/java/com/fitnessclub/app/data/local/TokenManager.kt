package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitnessclub.app.data.model.User
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Старый файл без сессии — очищаем, чтобы не путать с новой политикой. */
private val Context.legacyFitnessPrefs by preferencesDataStore(name = "fitness_prefs")

/** Постоянная сессия: access + refresh + user переживают перезапуск приложения. */
private val Context.authSessionPrefs by preferencesDataStore(name = "auth_session")

/**
 * Токены в памяти для быстрых HTTP-заголовков и на диске для автологина после рестарта.
 * Refresh на сервере без срока жизни (пока не logout / смена сессии); access ~1 ч — обновляем при старте.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) {

    private val accessToken = MutableStateFlow<String?>(null)
    private val refreshToken = MutableStateFlow<String?>(null)
    private val userJson = MutableStateFlow<String?>(null)

    private val hydrated = MutableStateFlow(false)
    private val persistMutex = Mutex()

    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyUser = stringPreferencesKey("user_json")

    val isHydrated: StateFlow<Boolean> = hydrated.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { context.legacyFitnessPrefs.edit { it.clear() } }
            hydrateFromDisk()
        }
    }

    /** Ждём первичную загрузку с диска (старт приложения). */
    suspend fun awaitHydrated() {
        if (hydrated.value) return
        hydrateFromDisk()
    }

    private suspend fun hydrateFromDisk() {
        persistMutex.withLock {
            if (hydrated.value) return
            val prefs = runCatching { context.authSessionPrefs.data.first() }.getOrNull()
            if (prefs != null) {
                // Не затираем токены, если параллельно уже успели сохранить новую сессию.
                if (accessToken.value.isNullOrBlank()) {
                    accessToken.value = prefs[keyAccess]?.takeIf { it.isNotBlank() }
                }
                if (refreshToken.value.isNullOrBlank()) {
                    refreshToken.value = prefs[keyRefresh]?.takeIf { it.isNotBlank() }
                }
                if (userJson.value.isNullOrBlank()) {
                    userJson.value = prefs[keyUser]?.takeIf { it.isNotBlank() }
                }
            }
            hydrated.value = true
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken.value = accessToken
        this.refreshToken.value = refreshToken
        persist()
    }

    suspend fun saveUser(user: User) {
        userJson.value = gson.toJson(user)
        persist()
    }

    suspend fun getAccessToken(): String? {
        awaitHydrated()
        return accessToken.value
    }

    fun peekAccessToken(): String? = accessToken.value

    fun peekRefreshToken(): String? = refreshToken.value

    /**
     * Обновление сессии из OkHttp Authenticator (фоновый поток): память сразу, диск асинхронно.
     */
    fun applyRefreshedSession(accessToken: String, refreshToken: String, user: User? = null) {
        this.accessToken.value = accessToken
        this.refreshToken.value = refreshToken
        if (user != null) {
            userJson.value = gson.toJson(user)
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { persist() }
        }
    }

    /** Сброс сессии без suspend (после провала refresh в Authenticator). */
    fun clearSessionAsync() {
        accessToken.value = null
        refreshToken.value = null
        userJson.value = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { clearAll() }
        }
    }

    suspend fun getRefreshToken(): String? {
        awaitHydrated()
        return refreshToken.value
    }

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

    /** Есть сохранённый refresh — можно пробовать восстановить сессию. */
    suspend fun hasPersistedRefresh(): Boolean {
        awaitHydrated()
        return !refreshToken.value.isNullOrBlank()
    }

    suspend fun clearAll() {
        accessToken.value = null
        refreshToken.value = null
        userJson.value = null
        persistMutex.withLock {
            runCatching {
                context.authSessionPrefs.edit { it.clear() }
            }
            runCatching {
                context.legacyFitnessPrefs.edit { it.clear() }
            }
            hydrated.value = true
        }
    }

    private suspend fun persist() {
        awaitHydrated()
        persistMutex.withLock {
            val access = accessToken.value
            val refresh = refreshToken.value
            val user = userJson.value
            context.authSessionPrefs.edit { prefs ->
                if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
                    prefs.clear()
                } else {
                    prefs[keyAccess] = access
                    prefs[keyRefresh] = refresh
                    if (!user.isNullOrBlank()) {
                        prefs[keyUser] = user
                    } else {
                        prefs.remove(keyUser)
                    }
                }
            }
        }
    }
}
