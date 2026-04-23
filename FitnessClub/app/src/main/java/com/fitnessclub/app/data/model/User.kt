package com.fitnessclub.app.data.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("phone")
    val phone: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    
    @SerializedName("bonus_points")
    val bonusPoints: Int = 0,

    /** none | pending | verified | rejected — после Сбер ID для покупки абонемента */
    @SerializedName("passport_verification_status")
    val passportVerificationStatus: String? = null,
    
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class AuthResponse(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("refresh_token")
    val refreshToken: String,
    
    @SerializedName("user")
    val user: User
)

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("password")
    val password: String
)

data class RegisterRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("phone")
    val phone: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("registration_type")
    val registrationType: String? = null,

    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,

    @SerializedName("gender")
    val gender: String? = null,

    @SerializedName("passport_series")
    val passportSeries: String? = null,

    @SerializedName("passport_number")
    val passportNumber: String? = null,

    @SerializedName("passport_issued_by")
    val passportIssuedBy: String? = null,

    @SerializedName("passport_issue_date")
    val passportIssueDate: String? = null,

    @SerializedName("registration_address")
    val registrationAddress: String? = null,

    @SerializedName("promo_code")
    val promoCode: String? = null,

    @SerializedName("newsletter")
    val newsletter: Boolean? = null,

    @SerializedName("club_id")
    val clubId: String? = null
)
