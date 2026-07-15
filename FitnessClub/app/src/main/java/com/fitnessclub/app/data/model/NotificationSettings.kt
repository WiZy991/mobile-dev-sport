package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class NotificationSettings(
    @SerializedName("push_enabled")
    val pushEnabled: Boolean = true,

    @SerializedName("email_enabled")
    val emailEnabled: Boolean = true,

    @SerializedName("training_reminders")
    val trainingReminders: Boolean = true,

    @SerializedName("schedule_changes")
    val scheduleChanges: Boolean = true,

    @SerializedName("promo_notifications")
    val promoNotifications: Boolean = false,
)
