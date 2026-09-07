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

    @SerializedName("club_name")
    val clubName: String? = null,

    /** Клуб из регистрации. API отдаёт число. */
    @SerializedName("club_id")
    val clubId: Int? = null,

    /** ascii | wiegand — формат QR входа домашнего клуба. */
    @SerializedName("entry_qr_format")
    val entryQrFormat: String? = null,

    /** none | pending | verified | rejected — после Сбер ID для покупки абонемента */
    @SerializedName("passport_verification_status")
    val passportVerificationStatus: String? = null,
    
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,

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
    
    @SerializedName("email_verified")
    val emailVerified: Boolean = false,

    @SerializedName("profile_locked")
    val profileLocked: Boolean = false,
    
    @SerializedName("created_at")
    val createdAt: String? = null
) {
    /** Достаточно данных, чтобы купить абонемент (без повторной регистрации). */
    fun isPassportCompleteForPurchase(): Boolean {
        val series = passportSeries?.filter { it.isDigit() }.orEmpty()
        val number = passportNumber?.filter { it.isDigit() }.orEmpty()
        return series.length == 4 &&
            number.length == 6 &&
            !passportIssuedBy.isNullOrBlank() &&
            !passportIssueDate.isNullOrBlank() &&
            !registrationAddress.isNullOrBlank() &&
            !dateOfBirth.isNullOrBlank()
    }
}

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

data class LoginHintRequest(
    @SerializedName("email")
    val email: String,
)

data class LoginHintResponse(
    @SerializedName("message")
    val message: String,
    @SerializedName("code")
    val code: String,
)

/** Результат подсказки для экрана входа (с кодом от сервера). */
data class LoginHintResult(
    val message: String,
    val code: String,
)

data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String = "",

    @SerializedName("new_password")
    val newPassword: String,
)

/** Частичный PUT /user/profile — только переданные поля (без паспорта). */
data class ProfilePatchRequest(
    @SerializedName("club_id")
    val clubId: Int? = null,
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
    val clubId: String? = null,

    /**
     * Зал так, как он был показан на экране выбора. CRM привязывает клиента по [clubId]
     * только если id ведёт на этот же зал — иначе находит зал по названию и адресу.
     */
    @SerializedName("club_name")
    val clubName: String? = null,

    @SerializedName("club_address")
    val clubAddress: String? = null,

    /** Опросник «Откуда вы о нас узнали»: ключ варианта (см. RegisterSurveyScreen). */
    @SerializedName("referral_source")
    val referralSource: String? = null,

    /** Свой вариант ответа, если выбрано «Другое». */
    @SerializedName("referral_source_other")
    val referralSourceOther: String? = null,

    @SerializedName("otp_ticket")
    val otpTicket: String? = null,
)

data class OtpRequestBody(
    @SerializedName("phone") val phone: String,
    @SerializedName("channel") val channel: String,
)

data class OtpRequestResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("resend_after_sec") val resendAfterSec: Int = 20,
    @SerializedName("ttl_sec") val ttlSec: Int = 300,
    @SerializedName("deeplink") val deeplink: String? = null,
    @SerializedName("instruction") val instruction: String? = null,
    @SerializedName("dev_code") val devCode: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null,
)

data class OtpVerifyBody(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String,
)

data class OtpVerifyResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("user") val user: User? = null,
    @SerializedName("registration_required") val registrationRequired: Boolean = false,
    @SerializedName("otp_ticket") val otpTicket: String? = null,
    @SerializedName("phone") val phone: String? = null,
)

data class OtpChannelsResponse(
    @SerializedName("channels") val channels: List<OtpChannelStatus> = emptyList(),
)

data class OtpChannelStatus(
    @SerializedName("id") val id: String,
    @SerializedName("available") val available: Boolean = false,
)

data class CheckEmailRequest(
    @SerializedName("email") val email: String,
)

data class CheckEmailResponse(
    @SerializedName("exists") val exists: Boolean = false,
    @SerializedName("masked_phone") val maskedPhone: String? = null,
    @SerializedName("message") val message: String? = null,
)

data class EmailResendResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("email") val email: String? = null,
    @SerializedName("already_verified") val alreadyVerified: Boolean = false,
)
