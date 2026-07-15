package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class Training(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("type")
    val type: TrainingType,
    
    @SerializedName("trainer")
    val trainer: Trainer,
    
    @SerializedName("start_time")
    val startTime: String,
    
    @SerializedName("end_time")
    val endTime: String,
    
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    
    @SerializedName("max_participants")
    val maxParticipants: Int,
    
    @SerializedName("current_participants")
    val currentParticipants: Int,
    
    @SerializedName("room")
    val room: String,
    
    @SerializedName("is_booked")
    val isBooked: Boolean = false,
    
    @SerializedName("intensity")
    val intensity: Intensity = Intensity.MEDIUM,
    
    @SerializedName("image_url")
    val imageUrl: String? = null
) {
    val spotsLeft: Int
        get() = maxParticipants - currentParticipants
    
    val isFull: Boolean
        get() = spotsLeft <= 0
}

enum class TrainingType {
    @SerializedName("group")
    GROUP,

    @SerializedName("personal")
    PERSONAL,

    /** Дополнительные услуги (солярий, массаж и т.п.) — слоты в расписании как у групповых */
    @SerializedName("extra")
    EXTRA
}

enum class Intensity {
    @SerializedName("low")
    LOW,
    
    @SerializedName("medium")
    MEDIUM,
    
    @SerializedName("high")
    HIGH
}

data class Trainer(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("photo_url")
    val photoUrl: String? = null,
    
    @SerializedName("specialization")
    val specialization: String? = null,
    
    @SerializedName("rating")
    val rating: Float = 0f,

    /** Текст «о себе» из CRM (карточка тренера в приложении). */
    @SerializedName("description")
    val description: String? = null,
)

data class Booking(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("training")
    val training: Training,
    
    @SerializedName("status")
    val status: BookingStatus,
    
    @SerializedName("booked_at")
    val bookedAt: String
)

enum class BookingStatus {
    @SerializedName("confirmed")
    CONFIRMED,
    
    @SerializedName("cancelled")
    CANCELLED,
    
    @SerializedName("completed")
    COMPLETED,
    
    @SerializedName("waiting_list")
    WAITING_LIST,

    /** Статус в БД бэкенда до нормализации API. */
    @SerializedName("waiting")
    WAITING,
}

fun BookingStatus.isUpcoming(): Boolean =
    this == BookingStatus.CONFIRMED || this == BookingStatus.WAITING_LIST || this == BookingStatus.WAITING
