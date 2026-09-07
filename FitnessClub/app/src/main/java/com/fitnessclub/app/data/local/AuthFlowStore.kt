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
    private val pendingClubKey = stringPreferencesKey("pending_registration_club_id")
    private val otpTicketKey = stringPreferencesKey("otp_registration_ticket")
    private val otpPhoneKey = stringPreferencesKey("otp_registration_phone")
    private val draftJsonKey = stringPreferencesKey("registration_draft_json")

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

    /** Зал, выбранный перед «Регистрация через Сбер ID» — переживает уход в браузер. */
    suspend fun savePendingRegistrationClubId(clubId: String) {
        val id = clubId.trim()
        if (id.isEmpty()) return
        context.authFlowPrefs.edit { prefs ->
            prefs[pendingClubKey] = id
        }
    }

    suspend fun peekPendingRegistrationClubId(): String? =
        context.authFlowPrefs.data.map { prefs -> prefs[pendingClubKey] }.first()

    suspend fun consumePendingRegistrationClubId(): String? {
        var clubId: String? = null
        context.authFlowPrefs.edit { prefs ->
            clubId = prefs[pendingClubKey]
            prefs.remove(pendingClubKey)
        }
        return clubId?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun clearPendingRegistrationClubId() {
        context.authFlowPrefs.edit { prefs ->
            prefs.remove(pendingClubKey)
        }
    }

    suspend fun saveOtpRegistration(ticket: String, phone: String) {
        context.authFlowPrefs.edit { prefs ->
            prefs[otpTicketKey] = ticket
            prefs[otpPhoneKey] = phone
        }
    }

    suspend fun peekOtpTicket(): String? =
        context.authFlowPrefs.data.map { prefs -> prefs[otpTicketKey] }.first()?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun peekOtpPhone(): String? =
        context.authFlowPrefs.data.map { prefs -> prefs[otpPhoneKey] }.first()?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun clearOtpRegistration() {
        context.authFlowPrefs.edit { prefs ->
            prefs.remove(otpTicketKey)
            prefs.remove(otpPhoneKey)
            prefs.remove(draftJsonKey)
        }
    }

    suspend fun saveRegistrationDraft(json: String) {
        context.authFlowPrefs.edit { prefs ->
            prefs[draftJsonKey] = json
        }
    }

    suspend fun peekRegistrationDraft(): String? =
        context.authFlowPrefs.data.map { prefs -> prefs[draftJsonKey] }.first()?.trim()?.takeIf { it.isNotEmpty() }
}
