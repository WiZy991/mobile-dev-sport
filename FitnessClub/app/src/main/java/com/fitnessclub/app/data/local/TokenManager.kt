package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitnessclub.app.data.model.User
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitness_prefs")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_KEY = stringPreferencesKey("user")
    }
    
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
        }
    }
    
    suspend fun saveUser(user: User) {
        context.dataStore.edit { preferences ->
            preferences[USER_KEY] = gson.toJson(user)
        }
    }
    
    suspend fun getAccessToken(): String? {
        return context.dataStore.data.first()[ACCESS_TOKEN_KEY]
    }
    
    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.first()[REFRESH_TOKEN_KEY]
    }
    
    fun getUser(): Flow<User?> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_KEY]?.let { json ->
                try {
                    gson.fromJson(json, User::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    fun isLoggedIn(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            !preferences[ACCESS_TOKEN_KEY].isNullOrEmpty()
        }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
