package com.fitnessclub.app.data.qr

/**
 * 9-значный QR для PERCo/Wiegand — синхронно с CRM `WiegandEntryQrCodec` и iOS `QRCodeGenerator`.
 */
object WiegandEntryQrCodec {
    private const val SLOT_MS = 15_000L
    private const val SLOT_MOD = 1000
    private const val USER_MOD = 100_000
    private val wiegandClubIds = setOf("11")

    fun usesWiegandNumeric(clubId: String?): Boolean {
        val raw = clubId?.trim().orEmpty()
        return raw.isNotEmpty() && raw in wiegandClubIds
    }

    fun encode(userId: String, timestampMs: Long): String {
        val uid = normalizedUserId(userId).toIntOrNull() ?: 0
        val userPart = uid % USER_MOD
        val slot = ((timestampMs.coerceAtLeast(0) / SLOT_MS) % SLOT_MOD).toInt()
        val body = "%05d%03d".format(userPart, slot)
        val check = luhnCheckDigit(body)
        return body + check
    }

    fun luhnCheckDigit(eightDigits: String): Int {
        if (eightDigits.length != 8 || !eightDigits.all { it.isDigit() }) return 0
        var sum = 0
        val rev = eightDigits.reversed()
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
