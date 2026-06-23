package com.fitnessclub.app.di

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.UUID

/**
 * Mock interceptor for testing the app without a real backend.
 * Set USE_MOCK = true to enable mock mode.
 */
class MockInterceptor : Interceptor {
    
    companion object {
        // Set to true to use mock data, false to use real API
        const val USE_MOCK = false  // Backend API готов к работе
    }
    
    private val jsonMediaType = "application/json".toMediaType()
    
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!USE_MOCK) {
            return chain.proceed(chain.request())
        }
        
        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method
        
        val responseBody = when {
            // Auth endpoints
            path.endsWith("/auth/login") && method == "POST" -> mockLoginResponse()
            path.endsWith("/auth/register") && method == "POST" -> mockRegisterResponse()
            path.endsWith("/auth/refresh") && method == "POST" -> mockRefreshResponse()
            path.endsWith("/auth/logout") && method == "POST" -> mockLogoutResponse()
            
            // User endpoints
            path.endsWith("/user/stats") && method == "GET" -> mockUserStatsResponse()
            path.endsWith("/user/profile") && method == "GET" -> mockProfileResponse()
            path.endsWith("/user/profile") && method == "PUT" -> mockProfileUpdateResponse()
            
            // Trainings endpoints - order matters!
            path.contains("/trainings") && path.contains("/book") && method == "POST" -> mockBookingResponse()
            path.contains("/trainings") && path.contains("/waiting-list") && method == "POST" -> mockBookingResponse()
            path.matches(Regex(".*/trainings/[^/]+$")) && method == "GET" -> mockTrainingDetailsResponse()
            path.endsWith("/trainings") && method == "GET" -> mockScheduleResponse()
            
            // Bookings endpoints
            path.matches(Regex(".*/bookings/[^/]+$")) && method == "DELETE" -> mockCancelBookingResponse()
            path.endsWith("/bookings") && method == "GET" -> mockBookingsResponse()
            
            // Subscriptions endpoints
            path.contains("/subscriptions") && path.contains("/cancel") && method == "POST" -> mockCancelSubscriptionResponse()
            path.endsWith("/subscriptions/purchase") && method == "POST" -> mockPurchaseResponse()
            path.contains("/subscriptions") && path.contains("/freeze") && method == "POST" -> mockFreezeResponse()
            path.contains("/subscriptions") && path.contains("/unfreeze") && method == "POST" -> mockUnfreezeResponse()
            path.endsWith("/subscriptions/plans") && method == "GET" -> mockSubscriptionPlansResponse()
            path.endsWith("/subscriptions") && method == "GET" -> mockSubscriptionsResponse()
            
            // Products
            path.contains("/products") && path.contains("/purchase") && method == "POST" -> mockProductPurchaseResponse()
            path.endsWith("/products") && method == "GET" -> mockProductsResponse()
            
            // Club info
            path.endsWith("/clubs") && method == "GET" -> mockClubsListResponse()
            path.endsWith("/club/occupancy") && method == "GET" -> mockOccupancyResponse()
            path.endsWith("/club/promotions") && method == "GET" -> mockClubPromotionsResponse()
            path.endsWith("/club/info") && method == "GET" -> mockClubInfoResponse()
            
            // Trainers endpoints
            path.matches(Regex(".*/trainers/[^/]+$")) && method == "GET" -> mockTrainerDetailsResponse()
            path.endsWith("/trainers") && method == "GET" -> mockTrainersResponse()
            
            // Feedback
            path.endsWith("/feedback") && method == "POST" -> """{"success": true, "id": "feedback-1"}"""

            path.endsWith("/support/tickets") && method == "GET" -> mockSupportTicketsListResponse()
            path.endsWith("/support/tickets") && method == "POST" -> """{"success": true, "id": "1"}"""
            
            // Guest passes
            path.endsWith("/guest-passes") && method == "POST" -> mockGuestPassCreateResponse()
            path.endsWith("/guest-passes") && method == "GET" -> "[]"
            
            // Notifications
            path.endsWith("/notifications/read-all") && method == "POST" -> """{"success": true}"""
            path.matches(Regex(".*/notifications/[^/]+/read$")) && method == "POST" -> """{"success": true}"""
            path.endsWith("/notifications") && method == "GET" -> mockNotificationsResponse()
            
            // Lockers
            path.endsWith("/lockers/release") && method == "POST" -> """{"success": true}"""
            path.matches(Regex(".*/lockers/[^/]+/book$")) && method == "POST" -> mockLockerBookResponse()
            path.endsWith("/lockers/my-booking") && method == "GET" -> "null"
            path.endsWith("/lockers") && method == "GET" -> mockLockersResponse()
            
            else -> """{"error": "Unknown endpoint: $path", "method": "$method"}"""
        }
        
        return Response.Builder()
            .code(200)
            .message("OK")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body(responseBody.toResponseBody(jsonMediaType))
            .build()
    }
    
    private fun mockRefreshResponse(): String {
        val token = UUID.randomUUID().toString()
        return """
        {
            "token": "$token",
            "refresh_token": "${UUID.randomUUID()}",
            "user": {
                "id": "user-123",
                "email": "user@example.com",
                "name": "Антон",
                "phone": "+7 922 222-22-22",
                "avatar_url": null,
                "bonus_points": 150,
                "created_at": "2025-06-01T10:00:00"
            }
        }
        """.trimIndent()
    }

    private fun mockLoginResponse(): String {
        val token = UUID.randomUUID().toString()
        return """
        {
            "token": "$token",
            "refresh_token": "${UUID.randomUUID()}",
            "user": {
                "id": "user-123",
                "email": "user@example.com",
                "name": "Антон",
                "phone": "+7 922 222-22-22",
                "avatar_url": null,
                "bonus_points": 150,
                "created_at": "2025-06-01T10:00:00"
            }
        }
        """.trimIndent()
    }
    
    private fun mockRegisterResponse(): String {
        val token = UUID.randomUUID().toString()
        return """
        {
            "token": "$token",
            "refresh_token": "${UUID.randomUUID()}",
            "user": {
                "id": "user-new-${UUID.randomUUID().toString().take(8)}",
                "email": "newuser@example.com",
                "name": "Новый пользователь",
                "phone": "+7 900 000-00-00",
                "avatar_url": null,
                "bonus_points": 0,
                "created_at": "2026-02-04T22:00:00"
            }
        }
        """.trimIndent()
    }
    
    private fun mockLogoutResponse(): String = """{"success": true}"""
    
    private fun mockUserStatsResponse(): String = """
    {
        "total_visits": 12,
        "streak_days": 3,
        "achievements": [
            {"id": "first_visit", "name": "Первое посещение", "description": "Добро пожаловать в зал!", "unlocked": true},
            {"id": "regular", "name": "Регулярный посетитель", "description": "5 посещений", "unlocked": true},
            {"id": "streak_3", "name": "Серия 3 дня", "description": "3 дня подряд", "unlocked": true}
        ]
    }
    """.trimIndent()
    
    private fun mockProfileResponse(): String = """
    {
        "id": "user-123",
        "email": "user@example.com",
        "name": "Антон",
        "phone": "+7 922 222-22-22",
        "avatar_url": null,
        "bonus_points": 150,
        "created_at": "2025-06-01T10:00:00"
    }
    """.trimIndent()
    
    private fun mockProfileUpdateResponse(): String = """
    {
        "id": "user-123",
        "email": "user@example.com",
        "name": "Антон",
        "phone": "+7 922 222-22-22",
        "avatar_url": null,
        "bonus_points": 150,
        "created_at": "2025-06-01T10:00:00"
    }
    """.trimIndent()
    
    private fun mockScheduleResponse(): String = """
    [
        {
            "id": "training-1",
            "name": "Йога для начинающих",
            "description": "Мягкая практика йоги для новичков. Развитие гибкости, укрепление мышц и расслабление.",
            "type": "group",
            "trainer": {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога", "rating": 4.8},
            "start_time": "2026-02-05T09:00:00",
            "end_time": "2026-02-05T10:00:00",
            "duration_minutes": 60,
            "room": "Зал йоги",
            "max_participants": 15,
            "current_participants": 8,
            "is_booked": false,
            "intensity": "low",
            "image_url": null
        },
        {
            "id": "training-2",
            "name": "Силовая тренировка",
            "description": "Интенсивная тренировка с отягощениями для развития силы и выносливости.",
            "type": "group",
            "trainer": {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9},
            "start_time": "2026-02-05T11:00:00",
            "end_time": "2026-02-05T12:00:00",
            "duration_minutes": 60,
            "room": "Тренажёрный зал",
            "max_participants": 20,
            "current_participants": 15,
            "is_booked": false,
            "intensity": "high",
            "image_url": null
        },
        {
            "id": "training-3",
            "name": "Кардио-хит",
            "description": "Высокоинтенсивная кардио тренировка для сжигания калорий.",
            "type": "group",
            "trainer": {"id": "trainer-3", "name": "Елена Сидорова", "photo_url": null, "specialization": "Кардио", "rating": 4.7},
            "start_time": "2026-02-05T14:00:00",
            "end_time": "2026-02-05T15:00:00",
            "duration_minutes": 60,
            "room": "Аэробный зал",
            "max_participants": 25,
            "current_participants": 20,
            "is_booked": true,
            "intensity": "high",
            "image_url": null
        },
        {
            "id": "training-4",
            "name": "Пилатес",
            "description": "Укрепление мышц кора, улучшение осанки и координации.",
            "type": "group",
            "trainer": {"id": "trainer-4", "name": "Ольга Козлова", "photo_url": null, "specialization": "Пилатес", "rating": 4.6},
            "start_time": "2026-02-05T16:00:00",
            "end_time": "2026-02-05T17:00:00",
            "duration_minutes": 60,
            "room": "Зал пилатеса",
            "max_participants": 12,
            "current_participants": 5,
            "is_booked": false,
            "intensity": "low",
            "image_url": null
        },
        {
            "id": "training-5",
            "name": "Бокс",
            "description": "Основы бокса: стойка, удары, защита. Отличная кардио нагрузка.",
            "type": "group",
            "trainer": {"id": "trainer-5", "name": "Дмитрий Волков", "photo_url": null, "specialization": "Единоборства", "rating": 4.9},
            "start_time": "2026-02-05T18:00:00",
            "end_time": "2026-02-05T19:30:00",
            "duration_minutes": 90,
            "room": "Зал единоборств",
            "max_participants": 16,
            "current_participants": 10,
            "is_booked": false,
            "intensity": "high",
            "image_url": null
        },
        {
            "id": "training-6",
            "name": "Растяжка",
            "description": "Глубокая растяжка всех групп мышц для восстановления после тренировок.",
            "type": "group",
            "trainer": {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога", "rating": 4.8},
            "start_time": "2026-02-05T20:00:00",
            "end_time": "2026-02-05T21:00:00",
            "duration_minutes": 60,
            "room": "Зал йоги",
            "max_participants": 20,
            "current_participants": 3,
            "is_booked": false,
            "intensity": "low",
            "image_url": null
        },
        {
            "id": "training-7",
            "name": "Утренняя зарядка",
            "description": "Бодрящая утренняя тренировка для заряда энергии на весь день.",
            "type": "group",
            "trainer": {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9},
            "start_time": "2026-02-06T07:00:00",
            "end_time": "2026-02-06T07:45:00",
            "duration_minutes": 45,
            "room": "Аэробный зал",
            "max_participants": 30,
            "current_participants": 12,
            "is_booked": false,
            "intensity": "medium",
            "image_url": null
        },
        {
            "id": "training-8",
            "name": "CrossFit",
            "description": "Функциональный тренинг высокой интенсивности.",
            "type": "group",
            "trainer": {"id": "trainer-5", "name": "Дмитрий Волков", "photo_url": null, "specialization": "Единоборства", "rating": 4.9},
            "start_time": "2026-02-06T10:00:00",
            "end_time": "2026-02-06T11:00:00",
            "duration_minutes": 60,
            "room": "CrossFit зона",
            "max_participants": 12,
            "current_participants": 12,
            "is_booked": false,
            "intensity": "high",
            "image_url": null
        }
    ]
    """.trimIndent()
    
    private fun mockTrainingDetailsResponse(): String = """
    {
        "id": "training-1",
        "name": "Йога для начинающих",
        "description": "Мягкая практика йоги для новичков. Развитие гибкости, укрепление мышц и расслабление. На занятии вы освоите базовые асаны и дыхательные техники.",
        "type": "group",
        "trainer": {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога", "rating": 4.8},
        "start_time": "2026-02-05T09:00:00",
        "end_time": "2026-02-05T10:00:00",
        "duration_minutes": 60,
        "room": "Зал йоги",
        "max_participants": 15,
        "current_participants": 8,
        "is_booked": false,
        "intensity": "low",
        "image_url": null
    }
    """.trimIndent()
    
    private fun mockBookingResponse(): String {
        val bookingId = UUID.randomUUID().toString().take(8)
        return """
        {
            "id": "booking-$bookingId",
            "status": "confirmed",
            "booked_at": "2026-02-04T22:00:00",
            "training": {
                "id": "training-1",
                "name": "Йога для начинающих",
                "description": "Мягкая практика йоги для новичков.",
                "type": "group",
                "trainer": {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога", "rating": 4.8},
                "start_time": "2026-02-05T09:00:00",
                "end_time": "2026-02-05T10:00:00",
                "duration_minutes": 60,
                "room": "Зал йоги",
                "max_participants": 15,
                "current_participants": 9,
                "is_booked": true,
                "intensity": "low",
                "image_url": null
            }
        }
        """.trimIndent()
    }
    
    private fun mockCancelBookingResponse(): String = """{"success": true}"""
    
    private fun mockBookingsResponse(): String = """
    [
        {
            "id": "booking-1",
            "status": "confirmed",
            "booked_at": "2026-02-04T12:00:00",
            "training": {
                "id": "training-1",
                "name": "Йога для начинающих",
                "description": "Мягкая практика йоги для новичков.",
                "type": "group",
                "trainer": {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога", "rating": 4.8},
                "start_time": "2026-02-05T09:00:00",
                "end_time": "2026-02-05T10:00:00",
                "duration_minutes": 60,
                "room": "Зал йоги",
                "max_participants": 15,
                "current_participants": 8,
                "is_booked": true,
                "intensity": "low",
                "image_url": null
            }
        },
        {
            "id": "booking-2",
            "status": "confirmed",
            "booked_at": "2026-02-04T14:00:00",
            "training": {
                "id": "training-5",
                "name": "Бокс",
                "description": "Основы бокса: стойка, удары, защита.",
                "type": "group",
                "trainer": {"id": "trainer-5", "name": "Дмитрий Волков", "photo_url": null, "specialization": "Единоборства", "rating": 4.9},
                "start_time": "2026-02-05T18:00:00",
                "end_time": "2026-02-05T19:30:00",
                "duration_minutes": 90,
                "room": "Зал единоборств",
                "max_participants": 16,
                "current_participants": 10,
                "is_booked": true,
                "intensity": "high",
                "image_url": null
            }
        },
        {
            "id": "booking-3",
            "status": "completed",
            "booked_at": "2026-01-28T10:00:00",
            "training": {
                "id": "training-old",
                "name": "Силовая тренировка",
                "description": "Интенсивная тренировка с отягощениями.",
                "type": "group",
                "trainer": {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9},
                "start_time": "2026-01-30T11:00:00",
                "end_time": "2026-01-30T12:00:00",
                "duration_minutes": 60,
                "room": "Тренажёрный зал",
                "max_participants": 20,
                "current_participants": 15,
                "is_booked": true,
                "intensity": "high",
                "image_url": null
            }
        }
    ]
    """.trimIndent()
    
    private fun mockSubscriptionsResponse(): String = """
    [
        {
            "id": "sub-1",
            "name": "Безлимит",
            "description": "Неограниченное посещение всех групповых занятий",
            "type": "unlimited",
            "start_date": "2026-01-01",
            "end_date": "2026-03-01",
            "status": "active",
            "visits_total": null,
            "visits_used": 0,
            "freeze_days_total": 14,
            "freeze_days_used": 0,
            "is_frozen": false,
            "price": 5000.0
        },
        {
            "id": "sub-2",
            "name": "Персональные тренировки",
            "description": "10 персональных тренировок с тренером",
            "type": "limited",
            "start_date": "2026-01-15",
            "end_date": "2026-04-15",
            "status": "active",
            "visits_total": 10,
            "visits_used": 3,
            "freeze_days_total": 0,
            "freeze_days_used": 0,
            "is_frozen": false,
            "price": 15000.0
        }
    ]
    """.trimIndent()
    
    private fun mockFreezeResponse(): String = """
    {
        "id": "sub-1",
        "name": "Безлимит",
        "description": "Неограниченное посещение всех групповых занятий",
        "type": "unlimited",
        "start_date": "2026-01-01",
        "end_date": "2026-03-08",
        "status": "frozen",
        "visits_total": null,
        "visits_used": 0,
        "freeze_days_total": 14,
        "freeze_days_used": 7,
        "is_frozen": true,
        "price": 5000.0
    }
    """.trimIndent()
    
    private fun mockUnfreezeResponse(): String = """
    {
        "id": "sub-1",
        "name": "Безлимит",
        "description": "Неограниченное посещение всех групповых занятий",
        "type": "unlimited",
        "start_date": "2026-01-01",
        "end_date": "2026-03-08",
        "status": "active",
        "visits_total": null,
        "visits_used": 0,
        "freeze_days_total": 14,
        "freeze_days_used": 7,
        "is_frozen": false,
        "price": 5000.0
    }
    """.trimIndent()

    private fun mockCancelSubscriptionResponse(): String = """
    {
        "id": "sub-1",
        "name": "Безлимит",
        "description": "Неограниченное посещение всех групповых занятий",
        "type": "unlimited",
        "start_date": "2026-01-01",
        "end_date": "2026-03-01",
        "status": "cancelled",
        "visits_total": null,
        "visits_used": 0,
        "freeze_days_total": 14,
        "freeze_days_used": 0,
        "is_frozen": false,
        "price": 5000.0
    }
    """.trimIndent()

    private fun mockSupportTicketsListResponse(): String = """
    {
        "tickets": [
            {
                "id": "1",
                "subject": "Не открывается QR",
                "message": "При сканировании турникет не реагирует",
                "category": "technical",
                "status": "in_progress",
                "created_at": "2026-06-01T10:00:00+03:00"
            }
        ]
    }
    """.trimIndent()
    
    private fun mockClubsListResponse(): String = """
    [
        {"id":"1","name":"ТЦ Формат","address":"ул. Центральная, 18, 2 этаж","phone":null,"email":null,"working_hours":null,"latitude":0,"longitude":0,"amenities":[],"max_capacity":null},
        {"id":"2","name":"ТЦ Новый де Фриз","address":"ул. Купера, 2, 2 этаж","phone":null,"email":null,"working_hours":null,"latitude":0,"longitude":0,"amenities":[],"max_capacity":null},
        {"id":"3","name":"ул. Купера, 2","address":"Основной зал","phone":null,"email":null,"working_hours":null,"latitude":0,"longitude":0,"amenities":[],"max_capacity":null}
    ]
    """.trimIndent()

    private fun mockSubscriptionPlansResponse(): String = """
    [
        {
            "id": "plan-1",
            "name": "На 12 месяцев",
            "description": "Неограниченное посещение. Заморозка: +30 дней.",
            "price": 38000.0,
            "duration_days": 365,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Тренажёрный зал", "Групповые программы", "Заморозка +30 дней"],
            "is_popular": true
        },
        {
            "id": "plan-2",
            "name": "На 6 месяцев",
            "description": "Неограниченное посещение. Заморозка: +20 дней.",
            "price": 25000.0,
            "duration_days": 180,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Тренажёрный зал", "Групповые программы", "Заморозка +20 дней"],
            "is_popular": false
        },
        {
            "id": "plan-3",
            "name": "На 4 месяца",
            "description": "Неограниченное посещение. Заморозка: +14 дней.",
            "price": 18000.0,
            "duration_days": 120,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Тренажёрный зал", "Групповые программы", "Заморозка +14 дней"],
            "is_popular": false
        },
        {
            "id": "plan-4",
            "name": "На 3 месяца",
            "description": "Неограниченное посещение. Заморозка: +14 дней.",
            "price": 16500.0,
            "duration_days": 90,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Тренажёрный зал", "Групповые программы", "Заморозка +14 дней"],
            "is_popular": false
        },
        {
            "id": "plan-5",
            "name": "На 1 месяц",
            "description": "Неограниченное посещение.",
            "price": 6000.0,
            "duration_days": 30,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Тренажёрный зал", "Групповые программы"],
            "is_popular": false
        },
        {
            "id": "plan-6",
            "name": "Разовое посещение",
            "description": "Один визит в зал.",
            "price": 990.0,
            "duration_days": null,
            "visits_count": 1,
            "type": "limited",
            "features": ["Одно посещение"],
            "is_popular": false
        }
    ]
    """.trimIndent()
    
    private fun mockPurchaseResponse(): String = """
    {
        "id": "sub-new",
        "name": "На 1 месяц",
        "description": "Неограниченное посещение",
        "type": "unlimited",
        "start_date": "2026-02-10",
        "end_date": "2026-03-12",
        "status": "active",
        "visits_total": null,
        "visits_used": 0,
        "freeze_days_total": 0,
        "freeze_days_used": 0,
        "is_frozen": false,
        "price": 5000.0
    }
    """.trimIndent()
    
    private fun mockProductPurchaseResponse(): String = """
    {"success": true, "sale_id": 1, "product": "Товар", "quantity": 1, "total": 350.0}
    """.trimIndent()

    private fun mockProductsResponse(): String = """
    [
        {"id": "product-1", "name": "Йога", "description": "Групповое занятие йогой", "price": 350.0, "category": "service"},
        {"id": "product-2", "name": "Персональная тренировка", "description": "Индивидуальная тренировка с тренером", "price": 2000.0, "category": "service"},
        {"id": "product-3", "name": "Полотенце", "description": "С логотипом клуба", "price": 800.0, "category": "goods"}
    ]
    """.trimIndent()
    
    private fun mockOccupancyResponse(): String = """
    {
        "current": 47,
        "max_capacity": 100,
        "percentage": 47,
        "status": "low"
    }
    """.trimIndent()
    
    private fun mockClubInfoResponse(): String = """
    {
        "name": "FitnessClub",
        "address": "г. Москва, ул. Примерная, д. 1",
        "phone": "+7 (495) 123-45-67",
        "email": "info@fitnessclub.ru",
        "working_hours": "Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00",
        "amenities": ["Тренажёрный зал", "Бассейн", "Йога", "Групповые занятия"],
        "latitude": 55.7558,
        "longitude": 37.6173,
        "promo_title": "СКИДКА 20%!",
        "promo_subtitle": "на все карты 12 и 6 месяцев"
    }
    """.trimIndent()

    private fun mockClubPromotionsResponse(): String = """
    [
      {
        "id": "promo-1",
        "title": "СКИДКА 20%!",
        "subtitle": "на все карты 12 и 6 месяцев",
        "image_url": null,
        "button_text": "Подробнее",
        "action_type": "shop",
        "action_value": null,
        "bg_from": "#F97316",
        "bg_to": "#3B82F6",
        "sort_order": 10
      },
      {
        "id": "promo-2",
        "title": "1+1 на персональные",
        "subtitle": "Оплатите 5 тренировок — получите 5 в подарок",
        "image_url": null,
        "button_text": "Купить",
        "action_type": "subscriptions",
        "action_value": null,
        "bg_from": "#EC4899",
        "bg_to": "#8B5CF6",
        "sort_order": 20
      }
    ]
    """.trimIndent()
    
    private fun mockTrainersResponse(): String = """
    [
        {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога, Растяжка", "rating": 4.8, "description": "Сертифицированный инструктор по хатха-йоге. Помогаю новичкам безопасно войти в практику."},
        {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9, "description": null},
        {"id": "trainer-3", "name": "Елена Сидорова", "photo_url": null, "specialization": "Кардио, Аэробика", "rating": 4.7, "description": null},
        {"id": "trainer-4", "name": "Ольга Козлова", "photo_url": null, "specialization": "Пилатес", "rating": 4.6, "description": null},
        {"id": "trainer-5", "name": "Дмитрий Волков", "photo_url": null, "specialization": "Единоборства, CrossFit", "rating": 4.9, "description": null}
    ]
    """.trimIndent()
    
    private fun mockTrainerDetailsResponse(): String = """
    {
        "id": "trainer-1",
        "name": "Мария Иванова",
        "photo_url": null,
        "specialization": "Йога, Растяжка",
        "rating": 4.8,
        "description": "Сертифицированный инструктор по хатха-йоге. Помогаю новичкам безопасно войти в практику."
    }
    """.trimIndent()
    
    private fun mockNotificationsResponse(): String = """
    [
        {"id": "notification-1", "type": "training_reminder", "title": "Напоминание о тренировке", "message": "Йога для начинающих начнётся через 1 час в Зале йоги", "created_at": "2026-02-10T09:00:00", "is_read": false, "reference_id": "training-1"},
        {"id": "notification-2", "type": "spot_freed", "title": "Освободилось место!", "message": "Освободилось место на тренировку «Силовая тренировка» (10.02.2026 11:00). Запишитесь, пока место свободно!", "created_at": "2026-02-10T08:30:00", "is_read": false, "reference_id": "training-2"},
        {"id": "notification-3", "type": "booking_confirmed", "title": "Запись подтверждена", "message": "Вы записаны на Силовую тренировку завтра в 11:00", "created_at": "2026-02-09T14:00:00", "is_read": true, "reference_id": null}
    ]
    """.trimIndent()
    
    private fun mockLockersResponse(): String = """
    [
        {"id": "locker-1", "number": "1", "status": "available"},
        {"id": "locker-2", "number": "2", "status": "occupied"},
        {"id": "locker-3", "number": "3", "status": "available"},
        {"id": "locker-4", "number": "4", "status": "available"},
        {"id": "locker-5", "number": "5", "status": "available"},
        {"id": "locker-6", "number": "6", "status": "occupied"},
        {"id": "locker-7", "number": "7", "status": "available"},
        {"id": "locker-8", "number": "8", "status": "available"},
        {"id": "locker-9", "number": "9", "status": "available"},
        {"id": "locker-10", "number": "10", "status": "available"},
        {"id": "locker-11", "number": "11", "status": "available"},
        {"id": "locker-12", "number": "12", "status": "available"},
        {"id": "locker-13", "number": "13", "status": "available"},
        {"id": "locker-14", "number": "14", "status": "available"},
        {"id": "locker-15", "number": "15", "status": "available"},
        {"id": "locker-16", "number": "16", "status": "available"},
        {"id": "locker-17", "number": "17", "status": "available"},
        {"id": "locker-18", "number": "18", "status": "available"},
        {"id": "locker-19", "number": "19", "status": "available"},
        {"id": "locker-20", "number": "20", "status": "available"}
    ]
    """.trimIndent()
    
    private fun mockLockerBookResponse(): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        return """
        {
            "id": "locker-booking-1",
            "locker": {"id": "locker-1", "number": "1", "status": "occupied"},
            "started_at": "2026-02-10T10:00:00",
            "ends_at": "2026-02-10T14:00:00",
            "qr_token": "$token",
            "qr_code_data": "FITNESSCLUB:LOCKER:1:$token"
        }
        """.trimIndent()
    }
    
    private fun mockGuestPassCreateResponse(): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        return """
        {
            "id": "guest-pass-1",
            "guest_name": null,
            "status": "active",
            "created_at": "2026-02-10T12:00:00",
            "used_at": null,
            "qr_code_data": "FITNESSCLUB:GUEST:1:$token"
        }
        """.trimIndent()
    }
}
