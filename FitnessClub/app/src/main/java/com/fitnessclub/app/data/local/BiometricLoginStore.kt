package com.fitnessclub.app.data.local

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит refresh-токен, зашифрованный ключом Keystore с обязательной биометрией.
 * После успешного расшифрования можно обновить сессию через [com.fitnessclub.app.data.repository.AuthRepository.restoreSessionWithRefreshToken].
 */
@Singleton
class BiometricLoginStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasStoredCredential(): Boolean {
        val ct = prefs.getString(KEY_CT, null)
        val iv = prefs.getString(KEY_IV, null)
        return prefs.getBoolean(KEY_ENABLED, false) && !ct.isNullOrBlank() && !iv.isNullOrBlank()
    }

    fun canUseDeviceBiometric(): Boolean {
        val bm = BiometricManager.from(context)
        val checks = intArrayOf(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
            BiometricManager.Authenticators.BIOMETRIC_WEAK,
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        for (a in checks) {
            if (bm.canAuthenticate(a) == BiometricManager.BIOMETRIC_SUCCESS) {
                return true
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            if (bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
                return true
            }
        }
        return false
    }

    /** Для UI: показывать кнопку входа по отпечатку (данные в приложении сохранены). */
    fun shouldShowBiometricLoginButton(): Boolean = hasStoredCredential()

    /**
     * Создаёт новый ключ и [Cipher] в режиме шифрования для [androidx.biometric.BiometricPrompt].
     * Сбрасывает предыдущие сохранённые данные, чтобы не остаться с ciphertext от старого ключа.
     */
    fun prepareEncryptCipher(): Cipher {
        prefs.edit().clear().apply()
        recreateBiometricKey()
        val key = getSecretKey() ?: error("Не удалось создать ключ Keystore")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun persistEncryptedPayload(iv: ByteArray, ciphertext: ByteArray) {
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(KEY_CT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun loadCipherTextBytes(): ByteArray {
        val b64 = prefs.getString(KEY_CT, null) ?: error("Нет сохранённых данных")
        return Base64.decode(b64, Base64.NO_WRAP)
    }

    /**
     * [Cipher] в режиме расшифровки; после успеха BiometricPrompt вызвать [Cipher.doFinal] с байтами из [loadCipherTextBytes].
     */
    fun prepareDecryptCipher(): Cipher {
        if (!hasStoredCredential()) error("Биовход не настроен")
        val key = getSecretKey() ?: error("Ключ недоступен. Включите вход по отпечатку заново.")
        val iv = Base64.decode(prefs.getString(KEY_IV, null), Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        deleteKeyQuietly()
    }

    private fun recreateBiometricKey() {
        deleteKeyQuietly()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    private fun deleteKeyQuietly() {
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                ks.deleteEntry(KEYSTORE_ALIAS)
            }
        }
    }

    private companion object {
        private const val PREFS_NAME = "biometric_login_store"
        private const val KEY_CT = "cipher_text_b64"
        private const val KEY_IV = "iv_b64"
        private const val KEY_ENABLED = "has_bundle"
        private const val KEYSTORE_ALIAS = "fitness_club_biometric_rt"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
