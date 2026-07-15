package com.fitnessclub.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authFlowPrefs by preferencesDataStore(name = "auth_flow")

/**
 * Локальные флаги auth-потока: завершена ли регистрация и PKCE-verifier Сбера
 * (переживает уход в браузер Сбер ID и пересоздание ViewModel).
 */
@Singleton
class AuthFlowStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val completedKey = booleanPreferencesKey("has_completed_registration")
    private val sberVerifierKey = stringPreferencesKey("pending_sber_code_verifier")

    val hasCompletedRegistration: Flow<Boolean> =
        context.authFlowPrefs.data.map { prefs -> prefs[completedKey] == true }

    suspend fun markRegistrationCompleted() {
        context.authFlowPrefs.edit { prefs ->
            prefs[completedKey] = true
        }
    }

    suspend fun savePendingSberVerifier(verifier: String) {
        context.authFlowPrefs.edit { prefs ->
            prefs[sberVerifierKey] = verifier
        }
    }

    suspend fun consumePendingSberVerifier(): String? {
        var verifier: String? = null
        context.authFlowPrefs.edit { prefs ->
            verifier = prefs[sberVerifierKey]
            prefs.remove(sberVerifierKey)
        }
        return verifier
    }

    suspend fun peekPendingSberVerifier(): String? =
        context.authFlowPrefs.data.map { prefs -> prefs[sberVerifierKey] }.first()

    suspend fun clearPendingSberVerifier() {
        context.authFlowPrefs.edit { prefs ->
            prefs.remove(sberVerifierKey)
        }
    }
}
