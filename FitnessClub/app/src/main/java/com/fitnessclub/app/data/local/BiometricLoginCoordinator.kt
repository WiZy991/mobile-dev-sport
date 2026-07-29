package com.fitnessclub.app.data.local

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Показ [BiometricPrompt] с [androidx.biometric.BiometricPrompt.CryptoObject] для шифрования/расшифровки refresh-токена.
 */
object BiometricLoginCoordinator {

    fun startEncryptPrompt(
        activity: FragmentActivity,
        store: BiometricLoginStore,
        refreshToken: String,
        userId: String? = null,
        onDone: (success: Boolean, errorMessage: String?) -> Unit,
    ) {
        val cipher = try {
            store.prepareEncryptCipher()
        } catch (e: Exception) {
            onDone(false, e.message ?: "Ошибка подготовки ключей")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val c = result.cryptoObject?.cipher
                        if (c == null) {
                            onDone(false, "Ошибка шифрования")
                            return
                        }
                        val enc = c.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
                        store.persistEncryptedPayload(c.iv, enc, userId)
                        onDone(true, null)
                    } catch (e: Exception) {
                        store.clear()
                        onDone(false, e.message ?: "Ошибка сохранения")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onDone(false, null)
                    } else {
                        onDone(false, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onDone(false, "Не удалось распознать отпечаток")
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход по отпечатку")
            .setSubtitle("Подтвердите личность, чтобы сохранить способ входа")
            .setNegativeButtonText("Отмена")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    fun startDecryptPrompt(
        activity: FragmentActivity,
        store: BiometricLoginStore,
        onDone: (refreshToken: String?, errorMessage: String?) -> Unit,
    ) {
        val cipher = try {
            store.prepareDecryptCipher()
        } catch (e: Exception) {
            onDone(null, e.message ?: "Нет сохранённого входа")
            return
        }
        val ct = try {
            store.loadCipherTextBytes()
        } catch (e: Exception) {
            onDone(null, e.message)
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                        val c = result.cryptoObject?.cipher
                        if (c == null) {
                            onDone(null, "Ошибка расшифровки")
                            return
                        }
                        val plain = c.doFinal(ct)
                        onDone(String(plain, Charsets.UTF_8), null)
                    } catch (e: Exception) {
                        onDone(null, e.message ?: "Ошибка расшифровки")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onDone(null, null)
                    } else {
                        onDone(null, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onDone(null, "Отпечаток не распознан")
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход в аккаунт")
            .setSubtitle("Используйте отпечаток пальца")
            .setNegativeButtonText("Отмена")
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
