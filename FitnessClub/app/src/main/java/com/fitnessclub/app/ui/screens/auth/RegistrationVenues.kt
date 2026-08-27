package com.fitnessclub.app.ui.screens.auth

import androidx.annotation.DrawableRes
import com.fitnessclub.app.R
import com.fitnessclub.app.data.api.ClubItem

/**
 * Залы при регистрации. [clubId] совпадает с `clubs.id` на сервере.
 * Синхронизация строк в БД CRM: `php bin/console app:clubs:sync-registration-venues`
 * (см. crm-backend-symfony `SyncRegistrationVenuesCommand`).
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
            clubId = "1",
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
}
