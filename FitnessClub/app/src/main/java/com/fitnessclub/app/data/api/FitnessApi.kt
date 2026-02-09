package com.fitnessclub.app.data.api

import com.fitnessclub.app.data.model.*
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
}

data class PushTokenRequest(
    val token: String,
    val platform: String = "android"
)
