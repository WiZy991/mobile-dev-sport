package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class Subscription(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("type")
    val type: SubscriptionType,
    
    @SerializedName("start_date")
    val startDate: String,
    
    @SerializedName("end_date")
    val endDate: String,
    
    @SerializedName("status")
    val status: SubscriptionStatus,
    
    @SerializedName("visits_total")
    val visitsTotal: Int? = null,
    
    @SerializedName("visits_used")
    val visitsUsed: Int = 0,
    
    @SerializedName("freeze_days_total")
    val freezeDaysTotal: Int = 0,
    
    @SerializedName("freeze_days_used")
    val freezeDaysUsed: Int = 0,
    
    @SerializedName("is_frozen")
    val isFrozen: Boolean = false,
    
    @SerializedName("price")
    val price: Double,
    
    @SerializedName("description")
    val description: String? = null
) {
    val visitsLeft: Int?
        get() = visitsTotal?.minus(visitsUsed)
    
    val freezeDaysLeft: Int
        get() = freezeDaysTotal - freezeDaysUsed
}

enum class SubscriptionType {
    @SerializedName("unlimited")
    UNLIMITED,
    
    @SerializedName("limited")
    LIMITED,
    
    @SerializedName("personal")
    PERSONAL,
    
    @SerializedName("group")
    GROUP
}

enum class SubscriptionStatus {
    @SerializedName("active")
    ACTIVE,
    
    @SerializedName("frozen")
    FROZEN,
    
    @SerializedName("expired")
    EXPIRED,
    
    @SerializedName("pending")
    PENDING
}

data class SubscriptionPlan(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("price")
    val price: Double,
    
    @SerializedName("duration_days")
    val durationDays: Int? = null,
    
    @SerializedName("visits_count")
    val visitsCount: Int? = null,
    
    @SerializedName("type")
    val type: SubscriptionType? = null,
    
    @SerializedName("features")
    val features: List<String>? = null,
    
    @SerializedName("is_popular")
    val isPopular: Boolean = false
) {
    val safeId: String get() = id ?: "plan-0"
    val safeName: String get() = name ?: ""
    val safeDescription: String get() = description ?: ""
    val safeDurationDays: Int get() = durationDays ?: 0
    val safeFeatures: List<String> get() = features ?: emptyList()
}
