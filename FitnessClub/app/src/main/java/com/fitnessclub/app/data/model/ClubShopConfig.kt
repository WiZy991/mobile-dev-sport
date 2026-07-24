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
    @SerializedName("social_links")
    val socialLinks: List<ClubSocialLink> = emptyList(),
    @SerializedName("social_vk")
    val socialVk: String? = null,
    @SerializedName("social_telegram")
    val socialTelegram: String? = null,
    @SerializedName("website")
    val website: String? = null,
)

data class ClubSocialLink(
    @SerializedName("type")
    val type: String = "other",
    @SerializedName("label")
    val label: String = "",
    @SerializedName("url")
    val url: String = "",
    @SerializedName("color")
    val color: String? = null,
) {
    val displayLabel: String
        get() = label.ifBlank { defaultSocialLabel(type) }

    val colorHex: String
        get() = color?.takeIf { it.isNotBlank() } ?: defaultSocialColorHex(type)

    val iconLetter: String
        get() = when (type.lowercase()) {
            "vk" -> "VK"
            "telegram" -> "TG"
            "whatsapp" -> "WA"
            "youtube" -> "YT"
            "instagram" -> "IG"
            "ok" -> "OK"
            "tiktok" -> "TT"
            "website" -> "WWW"
            else -> displayLabel.take(1).uppercase()
        }
}

fun ClubNetworkInfo.resolvedSocialLinks(): List<ClubSocialLink> {
    if (socialLinks.isNotEmpty()) {
        return socialLinks.filter { it.url.isNotBlank() }
    }
    val legacy = mutableListOf<ClubSocialLink>()
    website?.takeIf { it.isNotBlank() }?.let {
        legacy += ClubSocialLink(type = "website", label = "Сайт", url = it, color = "#64748B")
    }
    socialVk?.takeIf { it.isNotBlank() }?.let {
        legacy += ClubSocialLink(type = "vk", label = "ВКонтакте", url = it, color = "#4C75A3")
    }
    socialTelegram?.takeIf { it.isNotBlank() }?.let {
        legacy += ClubSocialLink(type = "telegram", label = "Telegram", url = it, color = "#0088CC")
    }
    return legacy
}

fun defaultSocialLabel(type: String): String = when (type.lowercase()) {
    "website" -> "Сайт"
    "vk" -> "ВКонтакте"
    "telegram" -> "Telegram"
    "whatsapp" -> "WhatsApp"
    "youtube" -> "YouTube"
    "instagram" -> "Instagram"
    "ok" -> "Одноклассники"
    "max" -> "MAX"
    "dzen" -> "Дзен"
    "rutube" -> "Rutube"
    "tiktok" -> "TikTok"
    else -> "Ссылка"
}

fun defaultSocialColorHex(type: String): String = when (type.lowercase()) {
    "website" -> "#64748B"
    "vk" -> "#4C75A3"
    "telegram" -> "#0088CC"
    "whatsapp" -> "#25D366"
    "youtube" -> "#FF0000"
    "instagram" -> "#E4405F"
    "ok" -> "#EE8208"
    "max" -> "#1A73E8"
    "dzen" -> "#000000"
    "rutube" -> "#1A1A1A"
    "tiktok" -> "#010101"
    else -> "#6B7280"
}
