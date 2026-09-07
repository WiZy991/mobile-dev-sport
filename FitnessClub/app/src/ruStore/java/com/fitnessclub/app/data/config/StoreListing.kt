package com.fitnessclub.app.data.config

/** Только RuStore. В этом flavor нет ссылок на Google Play. */
object StoreListing {
    @Suppress("UNUSED_PARAMETER")
    fun playStoreUrl(applicationId: String): String = ""

    fun ratingHint(): String =
        "Оставьте отзыв в RuStore — это помогает развивать приложение."

    fun ratingOptions(
        @Suppress("UNUSED_PARAMETER") applicationId: String,
        rustoreUrl: String,
    ): List<AppDistribution.StoreRatingOption> = listOf(
        AppDistribution.StoreRatingOption("Оценить в RuStore", rustoreUrl),
    )

    fun updateOptions(
        @Suppress("UNUSED_PARAMETER") applicationId: String,
        rustoreUrl: String,
    ): List<AppDistribution.StoreRatingOption> = listOf(
        AppDistribution.StoreRatingOption("Обновить в RuStore", rustoreUrl),
    )
}
