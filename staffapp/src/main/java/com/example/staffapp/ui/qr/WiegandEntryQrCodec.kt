package com.example.staffapp.ui.qr

/**
 * 7-значный QR для PERCo/Wiegand-26 — синхронно с CRM `WiegandEntryQrCodec`
 * и клиентским `com.fitnessclub.app.data.qr.WiegandEntryQrCodec`.
 *
 * Формат: UUUUTTC (userId % 10000 + слот 15с + Luhn).
 * Выбор формата — полем клуба `entry_qr_format` (`wiegand` | `ascii`).
 */
object WiegandEntryQrCodec {
    private const val SLOT_MS = 15_000L
    private const val SLOT_MOD = 100
    private const val USER_MOD = 10_000

    fun usesWiegandNumeric(entryQrFormat: String?): Boolean {
        val raw = entryQrFormat?.trim().orEmpty()
        return raw.equals("wiegand", ignoreCase = true)
    }

    fun encode(staffUserId: Int, timestampMs: Long): String {
        val userPart = staffUserId.coerceAtLeast(0) % USER_MOD
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
}
