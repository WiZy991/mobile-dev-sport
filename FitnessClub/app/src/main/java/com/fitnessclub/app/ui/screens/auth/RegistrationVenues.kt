package com.fitnessclub.app.ui.screens.auth

import androidx.annotation.DrawableRes
import com.fitnessclub.app.R
import com.fitnessclub.app.data.api.ClubItem

/**
 * Залы при регистрации.
 *
 * Основной источник списка — `GET /clubs` (залы с галочкой «Показывать в приложении»):
 * только там id совпадают с `clubs.id` в CRM. Карточки ниже — резерв на случай, когда
 * список не пришёл (нет сети / в CRM не отмечен ни один зал), плюс фото для карточек.
 */
data class RegistrationVenueCard(
    val clubId: String,
    val title: String,
    val addressLines: String,
    @DrawableRes val imageRes: Int,
    /** Показывать на экране «Выберите зал» при регистрации. */
    val openForRegistration: Boolean = true,
)

object RegistrationVenues {
    private val allCards: List<RegistrationVenueCard> = listOf(
        RegistrationVenueCard(
            clubId = "12",
            title = "ТЦ Формат",
            addressLines = "ул. Центральная, 18, 2 этаж",
            imageRes = R.drawable.registration_club_mall,
            openForRegistration = true,
        ),
        RegistrationVenueCard(
            clubId = "2",
            title = "ТЦ Новый де Фриз",
            addressLines = "ул. Купера, 2, 2 этаж",
            imageRes = R.drawable.registration_club_kupera,
            openForRegistration = true,
        ),
        RegistrationVenueCard(
            clubId = "11",
            title = "ТЦ Седанка Сити",
            addressLines = "г. Владивосток, ул. Полетаева 6Д",
            imageRes = R.drawable.registration_club_mall,
        ),
    )

    val orderedCards: List<RegistrationVenueCard> = allCards.filter { it.openForRegistration }

    fun cardByClubId(clubId: String): RegistrationVenueCard? =
        allCards.find { it.clubId == clubId }

    fun toClubItem(card: RegistrationVenueCard): ClubItem = ClubItem(
        id = card.clubId,
        name = card.title,
        address = card.addressLines,
    )

    /**
     * Фото зала для карточки регистрации: по названию и адресу, а не по id
     * (id залов в CRM у каждой сети свои, привязываться к ним нельзя).
     */
    @DrawableRes
    fun imageResFor(name: String, address: String): Int {
        val hay = "$name $address".lowercase()
        val kupera = listOf("купера", "де фриз", "де-фриз", "дефриз")
        return if (kupera.any { hay.contains(it) }) {
            R.drawable.registration_club_kupera
        } else {
            R.drawable.registration_club_mall
        }
    }
}
