package com.example.staffapp

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Доступ к QR прохода: оплата аренды по календарю клуба (Владивосток). */
object StaffRentalAccess {

    /** Клуб во Владивостоке — не путать с UTC сервера и с TZ телефона. */
    private val clubZone: ZoneId = ZoneId.of("Asia/Vladivostok")

    fun isPaidPeriodActive(rentalPaidUntilIso: String?): Boolean {
        if (rentalPaidUntilIso.isNullOrBlank()) return false
        val untilDate = parsePaidUntilDate(rentalPaidUntilIso) ?: return false
        val today = LocalDate.now(clubZone)
        // «Оплачена до 23.08» = весь день 23.08 по Владивостоку.
        return !untilDate.isBefore(today)
    }

    fun canShowEntryQr(
        staffUserId: Int,
        status: String,
        requiresRental: Boolean,
        rentalPaidUntilIso: String?,
        rentalActiveFromServer: Boolean? = null,
        activeClubRentalOk: Boolean? = null,
    ): Boolean {
        if (staffUserId <= 0) return false
        if (status == "pending_approval" || status == "rejected") return false
        if (!requiresRental) return true
        if (activeClubRentalOk != null) return activeClubRentalOk
        return isPaidPeriodActive(rentalPaidUntilIso) || rentalActiveFromServer == true
    }

    fun entryQrBlockedMessage(
        staffUserId: Int,
        status: String,
        requiresRental: Boolean,
        rentalPaidUntilIso: String?,
        hasPaidClubsButWrongActive: Boolean = false,
    ): String? {
        if (staffUserId <= 0) return "Не удалось определить учётную запись."
        if (status == "pending_approval" || status == "rejected") {
            return "QR откроется после одобрения регистрации."
        }
        if (!requiresRental) return null
        if (hasPaidClubsButWrongActive) {
            return "Выберите оплаченный зал в профиле, чтобы открыть QR прохода."
        }
        if (!isPaidPeriodActive(rentalPaidUntilIso)) {
            return if (rentalPaidUntilIso.isNullOrBlank()) {
                "Оплатите аренду зала, чтобы пройти по QR."
            } else {
                "Срок аренды выбранного зала истёк. Продлите оплату или смените адрес."
            }
        }
        return null
    }

    private fun parsePaidUntilDate(iso: String): LocalDate? {
        val normalized = iso.trim().replace(' ', 'T')
        runCatching {
            return LocalDateTime.parse(normalized.take(19)).toLocalDate()
        }
        runCatching {
            return LocalDate.parse(normalized.take(10))
        }
        return null
    }
}
