package com.fitnessclub.app.data.api

import com.fitnessclub.app.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface FitnessApi {
    
    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("auth/refresh")
    suspend fun refreshToken(@Header("Authorization") refreshToken: String): Response<AuthResponse>
    
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
    
    // User
    @GET("user/profile")
    suspend fun getProfile(): Response<User>
    
    @PUT("user/profile")
    suspend fun updateProfile(@Body user: User): Response<User>
    
    @GET("user/stats")
    suspend fun getUserStats(): Response<UserStats>

    @GET("user/purchases")
    suspend fun getPurchases(): Response<List<PurchaseItem>>
    
    // Schedule & Trainings
    @GET("trainings")
    suspend fun getTrainings(
        @Query("date") date: String? = null,
        @Query("type") type: String? = null
    ): Response<List<Training>>
    
    @GET("trainings/{id}")
    suspend fun getTrainingDetails(@Path("id") id: String): Response<Training>
    
    // Bookings
    @GET("bookings")
    suspend fun getMyBookings(): Response<List<Booking>>
    
    @POST("trainings/{id}/book")
    suspend fun bookTraining(@Path("id") trainingId: String): Response<Booking>
    
    @DELETE("bookings/{id}")
    suspend fun cancelBooking(@Path("id") bookingId: String): Response<Unit>
    
    @POST("trainings/{id}/waiting-list")
    suspend fun joinWaitingList(@Path("id") trainingId: String): Response<Booking>
    
    // Subscriptions
    @GET("subscriptions")
    suspend fun getMySubscriptions(): Response<List<Subscription>>
    
    @GET("subscriptions/plans")
    suspend fun getSubscriptionPlans(): Response<List<SubscriptionPlan>>
    
    @POST("subscriptions/purchase")
    suspend fun purchaseSubscription(@Body request: PurchaseSubscriptionRequest): Response<Subscription>
    
    @POST("subscriptions/{id}/freeze")
    suspend fun freezeSubscription(
        @Path("id") subscriptionId: String,
        @Query("days") days: Int
    ): Response<Subscription>
    
    @POST("subscriptions/{id}/unfreeze")
    suspend fun unfreezeSubscription(@Path("id") subscriptionId: String): Response<Subscription>
    
    // Trainers
    @GET("trainers")
    suspend fun getTrainers(): Response<List<Trainer>>
    
    @GET("trainers/{id}")
    suspend fun getTrainerDetails(@Path("id") id: String): Response<Trainer>
    
    // Push tokens
    @POST("user/push-token")
    suspend fun registerPushToken(@Body token: PushTokenRequest): Response<Unit>
    
    // Products (Shop)
    @GET("products")
    suspend fun getProducts(): Response<List<Product>>
    
    @POST("products/{id}/purchase")
    suspend fun purchaseProduct(
        @Path("id") productId: String,
        @Body request: PurchaseProductRequest? = null
    ): Response<PurchaseProductResponse>
    
    // Club info
    @GET("club/info")
    suspend fun getClubInfo(): Response<ClubInfo>

    @GET("club/promotions")
    suspend fun getClubPromotions(): Response<List<ClubPromotion>>

    // Clubs (network)
    @GET("clubs")
    suspend fun getClubs(): Response<List<ClubItem>>

    @GET("clubs/{id}")
    suspend fun getClubDetails(@Path("id") id: String): Response<ClubInfo>
    
    // Notifications
    @GET("notifications")
    suspend fun getNotifications(): Response<List<ApiNotification>>
    
    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<Unit>
    
    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<Unit>
    
    // Feedback
    @POST("feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): Response<FeedbackResponse>

<<<<<<< HEAD
    /** Обращение в поддержку клуба (попадает в CRM). */
    @POST("support/tickets")
    suspend fun createSupportTicket(@Body request: SupportTicketRequest): Response<SupportTicketCreateResponse>
=======
    /** Обращение в поддержку (попадает в CRM → «Обращения из приложения»). */
    @POST("support/tickets")
    suspend fun createSupportTicket(@Body request: SupportTicketRequest): Response<SupportTicketResponse>
>>>>>>> a188090 (update)
    
    // Guest passes
    @GET("guest-passes")
    suspend fun getGuestPasses(): Response<List<GuestPass>>
    
    @POST("guest-passes")
    suspend fun createGuestPass(@Body request: CreateGuestPassRequest): Response<GuestPass>
    
    @GET("club/occupancy")
    suspend fun getClubOccupancy(): Response<GymOccupancy>
    
    // Lockers
    @GET("lockers")
    suspend fun getLockers(): Response<List<Locker>>
    
    @GET("lockers/my-booking")
    suspend fun getMyLockerBooking(): Response<LockerBooking?>
    
    @POST("lockers/{id}/book")
    suspend fun bookLocker(@Path("id") lockerId: String): Response<LockerBooking>
    
    @POST("lockers/release")
    suspend fun releaseLocker(): Response<Unit>

    // Documents
    @GET("documents")
    suspend fun getDocuments(): Response<List<ApiDocument>>

    @Multipart
    @POST("documents")
    suspend fun uploadDocument(
        @Part file: MultipartBody.Part,
        @Part("name") name: okhttp3.RequestBody,
        @Part("category") category: okhttp3.RequestBody?
    ): Response<ApiDocument>

    @Streaming
    @GET("documents/{id}/download")
    suspend fun downloadDocument(@Path("id") id: String): Response<okhttp3.ResponseBody>
}

data class PurchaseSubscriptionRequest(
    @com.google.gson.annotations.SerializedName("plan_id")
    val planId: String,
    @com.google.gson.annotations.SerializedName("promo_code")
    val promoCode: String? = null
)

data class PurchaseItem(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("product_name")
    val productName: String,
    @com.google.gson.annotations.SerializedName("quantity")
    val quantity: Int,
    @com.google.gson.annotations.SerializedName("price")
    val price: Double,
    @com.google.gson.annotations.SerializedName("total")
    val total: Double,
    @com.google.gson.annotations.SerializedName("payment_method")
    val paymentMethod: String,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String
)

data class UserStats(
    @com.google.gson.annotations.SerializedName("total_visits")
    val totalVisits: Int,
    @com.google.gson.annotations.SerializedName("streak_days")
    val streakDays: Int,
    @com.google.gson.annotations.SerializedName("achievements")
    val achievements: List<Achievement>
)

data class Achievement(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("description")
    val description: String,
    @com.google.gson.annotations.SerializedName("unlocked")
    val unlocked: Boolean
)

data class PushTokenRequest(
    val token: String,
    val platform: String = "android"
)

data class Product(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("description")
    val description: String? = null,
    @com.google.gson.annotations.SerializedName("price")
    val price: Double,
    @com.google.gson.annotations.SerializedName("category")
    val category: String = "service"
)

data class PurchaseProductRequest(
    val quantity: Int = 1,
    @com.google.gson.annotations.SerializedName("payment_method")
    val paymentMethod: String = "card"
)

data class PurchaseProductResponse(
    val success: Boolean = true,
    @com.google.gson.annotations.SerializedName("sale_id")
    val saleId: Int = 0,
    val product: String = "",
    val quantity: Int = 1,
    val total: Double = 0.0
)

data class Locker(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("number")
    val number: String,
    @com.google.gson.annotations.SerializedName("status")
    val status: String
)

data class LockerBooking(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("locker")
    val locker: Locker,
    @com.google.gson.annotations.SerializedName("started_at")
    val startedAt: String,
    @com.google.gson.annotations.SerializedName("ends_at")
    val endsAt: String,
    @com.google.gson.annotations.SerializedName("qr_token")
    val qrToken: String,
    @com.google.gson.annotations.SerializedName("qr_code_data")
    val qrCodeData: String
)

data class GuestPass(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("guest_name")
    val guestName: String?,
    @com.google.gson.annotations.SerializedName("status")
    val status: String,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String,
    @com.google.gson.annotations.SerializedName("used_at")
    val usedAt: String?,
    @com.google.gson.annotations.SerializedName("qr_code_data")
    val qrCodeData: String
)

data class CreateGuestPassRequest(
    @com.google.gson.annotations.SerializedName("guest_name")
    val guestName: String? = null
)

data class FeedbackRequest(
    val rating: Int,
    val comment: String = "",
    val type: String = "general",
    @com.google.gson.annotations.SerializedName("reference_id")
    val referenceId: String? = null
)

data class FeedbackResponse(
    val success: Boolean = true,
    val id: String = ""
)

data class SupportTicketRequest(
    val subject: String,
    val message: String,
<<<<<<< HEAD
    val category: String,
    @com.google.gson.annotations.SerializedName("contact_email")
    val contactEmail: String? = null,
)

data class SupportTicketCreateResponse(
    val success: Boolean = true,
=======
    val category: String = "other",
    @com.google.gson.annotations.SerializedName("contact_email")
    val contactEmail: String? = null
)

data class SupportTicketResponse(
    val success: Boolean = true,
    val id: String = ""
>>>>>>> a188090 (update)
)

data class ApiNotification(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("type")
    val type: String,
    @com.google.gson.annotations.SerializedName("title")
    val title: String,
    @com.google.gson.annotations.SerializedName("message")
    val message: String,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String,
    @com.google.gson.annotations.SerializedName("is_read")
    val isRead: Boolean,
    @com.google.gson.annotations.SerializedName("reference_id")
    val referenceId: String? = null
)

data class GymOccupancy(
    @com.google.gson.annotations.SerializedName("current")
    val current: Int,
    @com.google.gson.annotations.SerializedName("max_capacity")
    val maxCapacity: Int,
    @com.google.gson.annotations.SerializedName("percentage")
    val percentage: Int,
    @com.google.gson.annotations.SerializedName("status")
    val status: String
)

data class ApiDocument(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("category")
    val category: String? = null,
    @com.google.gson.annotations.SerializedName("created_at")
    val createdAt: String,
    @com.google.gson.annotations.SerializedName("is_mine")
    val isMine: Boolean = false
)

data class ClubItem(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("address")
    val address: String,
    @com.google.gson.annotations.SerializedName("phone")
    val phone: String? = null,
    @com.google.gson.annotations.SerializedName("email")
    val email: String? = null,
    @com.google.gson.annotations.SerializedName("working_hours")
    val workingHours: String? = null,
    @com.google.gson.annotations.SerializedName("latitude")
    val latitude: Double = 0.0,
    @com.google.gson.annotations.SerializedName("longitude")
    val longitude: Double = 0.0,
    @com.google.gson.annotations.SerializedName("amenities")
    val amenities: List<String> = emptyList(),
    @com.google.gson.annotations.SerializedName("max_capacity")
    val maxCapacity: Int? = null
)

data class ClubInfo(
    @com.google.gson.annotations.SerializedName("id")
    val id: String? = null,
    @com.google.gson.annotations.SerializedName("promo_title")
    val promoTitle: String? = null,
    @com.google.gson.annotations.SerializedName("promo_subtitle")
    val promoSubtitle: String? = null,
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("address")
    val address: String,
    @com.google.gson.annotations.SerializedName("phone")
    val phone: String,
    @com.google.gson.annotations.SerializedName("email")
    val email: String,
    @com.google.gson.annotations.SerializedName("working_hours")
    val workingHours: String,
    @com.google.gson.annotations.SerializedName("amenities")
    val amenities: List<String> = emptyList(),
    @com.google.gson.annotations.SerializedName("latitude")
    val latitude: Double = 0.0,
    @com.google.gson.annotations.SerializedName("longitude")
    val longitude: Double = 0.0
)

data class ClubPromotion(
    @com.google.gson.annotations.SerializedName("id")
    val id: String,
    @com.google.gson.annotations.SerializedName("title")
    val title: String,
    @com.google.gson.annotations.SerializedName("subtitle")
    val subtitle: String? = null,
    @com.google.gson.annotations.SerializedName("image_url")
    val imageUrl: String? = null,
    @com.google.gson.annotations.SerializedName("button_text")
    val buttonText: String = "Подробнее",
    @com.google.gson.annotations.SerializedName("action_type")
    val actionType: String = "shop",
    @com.google.gson.annotations.SerializedName("action_value")
    val actionValue: String? = null,
    @com.google.gson.annotations.SerializedName("bg_from")
    val bgFrom: String = "#F97316",
    @com.google.gson.annotations.SerializedName("bg_to")
    val bgTo: String = "#3B82F6",
    @com.google.gson.annotations.SerializedName("sort_order")
    val sortOrder: Int = 100
)
