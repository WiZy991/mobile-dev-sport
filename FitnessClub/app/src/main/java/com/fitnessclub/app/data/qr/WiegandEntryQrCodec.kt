package com.fitnessclub.app.data.qr

/**
 * 7-значный QR для PERCo/Wiegand-26 — синхронно с CRM `WiegandEntryQrCodec` и iOS `QRCodeGenerator`.
 * Число всегда ≤ 9_999_999 < 2^24, иначе C01 обрезает код (9 цифр → ~7 «мусорных»).
 * Формат выбирается полем клуба `entry_qr_format` (`wiegand` | `ascii`), не hardcode club_id.
 */
object WiegandEntryQrCodec {
    private const val SLOT_MS = 15_000L
    private const val SLOT_MOD = 100
    private const val USER_MOD = 10_000

    fun usesWiegandNumeric(entryQrFormat: String?): Boolean {
        val raw = entryQrFormat?.trim().orEmpty()
        return raw.equals("wiegand", ignoreCase = true)
    }

    fun encode(userId: String, timestampMs: Long): String {
        val uid = normalizedUserId(userId).toIntOrNull() ?: 0
        val userPart = uid % USER_MOD
        val slot = ((timestampMs.coerceAtLeast(0) / SLOT_MS) % SLOT_MOD).toInt()
        val body = "%04d%02d".format(userPart, slot)
        val check = luhnCheckDigit(body)
        return body + check
    }

    fun luhnCheckDigit(bodyDigits: String): Int {
        if (bodyDigits.isEmpty() || !bodyDigits.all { it.isDigit() }) return 0
        var sum = 0
        val rev = bodyDigits.reversed()
        for (i in rev.indices) {
            var n = rev[i].digitToInt()
            if (i % 2 == 0) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
        }
        return (10 - sum % 10) % 10
    }

    private fun normalizedUserId(userId: String): String =
        if (userId.lowercase().startsWith("user-")) userId.substring(5) else userId
}
