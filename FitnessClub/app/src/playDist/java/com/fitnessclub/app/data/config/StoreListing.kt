package com.fitnessclub.app.data.config

/** Витрины Google Play + RuStore. Только в сборке для Play. */
object StoreListing {
    fun playStoreUrl(applicationId: String): String =
        "https://play.google.com/store/apps/details?id=$applicationId"

    fun ratingHint(): String =
        "Оставьте отзыв в магазине, из которого вы скачали приложение."

    fun ratingOptions(applicationId: String, rustoreUrl: String): List<AppDistribution.StoreRatingOption> =
        listOf(
            AppDistribution.StoreRatingOption("Оценить в Google Play", playStoreUrl(applicationId)),
            AppDistribution.StoreRatingOption("Оценить в RuStore", rustoreUrl),
        )

    fun updateOptions(applicationId: String, rustoreUrl: String): List<AppDistribution.StoreRatingOption> =
        listOf(
            AppDistribution.StoreRatingOption("Обновить в Google Play", playStoreUrl(applicationId)),
            AppDistribution.StoreRatingOption("Обновить в RuStore", rustoreUrl),
        )
}
