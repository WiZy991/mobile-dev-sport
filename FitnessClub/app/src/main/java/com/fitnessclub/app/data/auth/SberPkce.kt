package com.fitnessclub.app.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object SberPkce {
    private const val VERIFIER_BYTES = 64
    private val secureRandom = SecureRandom()

    fun createCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        secureRandom.nextBytes(bytes)
        return base64UrlNoPadding(bytes)
    }

    fun createCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64UrlNoPadding(digest)
    }

    private fun base64UrlNoPadding(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
