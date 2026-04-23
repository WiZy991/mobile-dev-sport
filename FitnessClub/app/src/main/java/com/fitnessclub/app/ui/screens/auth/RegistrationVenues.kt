package com.fitnessclub.app.ui.screens.auth

import androidx.annotation.DrawableRes
import com.fitnessclub.app.R
import com.fitnessclub.app.data.api.ClubItem

/**
 * Три зала при регистрации. [clubId] совпадает с `clubs.id` на сервере после миграций
 * (1 — ТЦ Формат, 2 — ТЦ Новый де фриз, 3 — зал ул. Купера, 2).
 */
data class RegistrationVenueCard(
    val clubId: String,
    val title: String,
    val addressLines: String,
    @DrawableRes val imageRes: Int,
)

object RegistrationVenues {
    val orderedCards: List<RegistrationVenueCard> = listOf(
        RegistrationVenueCard(
            clubId = "3",
            title = "ул. Купера, 2",
            addressLines = "Основной зал",
            imageRes = R.drawable.registration_club_kupera,
        ),
        RegistrationVenueCard(
            clubId = "1",
            title = "ТЦ Формат",
            addressLines = "ул. Центральная, 18, 2 этаж",
            imageRes = R.drawable.registration_club_mall,
        ),
        RegistrationVenueCard(
            clubId = "2",
            title = "ТЦ Новый де Фриз",
            addressLines = "ул. Купера, 2, 2 этаж",
            imageRes = R.drawable.registration_club_mall,
        ),
    )

    fun toClubItem(card: RegistrationVenueCard): ClubItem = ClubItem(
        id = card.clubId,
        name = card.title,
        address = card.addressLines,
    )
}
