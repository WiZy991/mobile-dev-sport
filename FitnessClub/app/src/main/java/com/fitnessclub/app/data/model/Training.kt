package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class Training(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("name")
    val name: String? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("type")
    val type: TrainingType? = null,
    
    @SerializedName("trainer")
    val trainer: Trainer? = null,
    
    @SerializedName("start_time")
    val startTime: String? = null,
    
    @SerializedName("end_time")
    val endTime: String? = null,
    
    @SerializedName("duration_minutes")
    val durationMinutes: Int = 0,
    
    @SerializedName("max_participants")
    val maxParticipants: Int = 0,
    
    @SerializedName("current_participants")
    val currentParticipants: Int = 0,
    
    @SerializedName("room")
    val room: String? = null,
    
    @SerializedName("is_booked")
    val isBooked: Boolean = false,
    
    @SerializedName("intensity")
    val intensity: Intensity? = null,
    
    @SerializedName("image_url")
    val imageUrl: String? = null
) {
    val safeId: String get() = id.orEmpty()
    val safeName: String get() = name.orEmpty()
    val safeStartTime: String get() = startTime.orEmpty()
    val safeEndTime: String get() = endTime.orEmpty()
    val safeTrainerName: String get() = trainer?.name.orEmpty().ifBlank { "Без тренера" }

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
    val name: String? = null,
    
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
