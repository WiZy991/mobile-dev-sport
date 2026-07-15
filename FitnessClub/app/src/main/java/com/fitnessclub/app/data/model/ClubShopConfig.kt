package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class ClubShopConfig(
    @SerializedName("tab_order")
    val tabOrder: List<String> = listOf("subscriptions", "services", "goods"),
    @SerializedName("default_tab")
    val defaultTab: String = "subscriptions",
    @SerializedName("hide_empty_tabs")
    val hideEmptyTabs: Boolean = true,
    @SerializedName("counts")
    val counts: ClubShopCounts = ClubShopCounts(),
)

data class ClubShopCounts(
    @SerializedName("services")
    val services: Int = 0,
    @SerializedName("goods")
    val goods: Int = 0,
    @SerializedName("subscriptions")
    val subscriptions: Int = 0,
)

data class ClubNetworkInfo(
    @SerializedName("about")
    val about: String? = null,
    @SerializedName("social_vk")
    val socialVk: String? = null,
    @SerializedName("social_telegram")
    val socialTelegram: String? = null,
    @SerializedName("website")
    val website: String? = null,
)
