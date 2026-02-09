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
        const val USE_MOCK = false
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
            path.endsWith("/auth/logout") && method == "POST" -> mockLogoutResponse()
            
            // User endpoints
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
            path.contains("/subscriptions") && path.contains("/freeze") && method == "POST" -> mockFreezeResponse()
            path.contains("/subscriptions") && path.contains("/unfreeze") && method == "POST" -> mockUnfreezeResponse()
            path.endsWith("/subscriptions/plans") && method == "GET" -> mockSubscriptionPlansResponse()
            path.endsWith("/subscriptions") && method == "GET" -> mockSubscriptionsResponse()
            
            // Trainers endpoints
            path.matches(Regex(".*/trainers/[^/]+$")) && method == "GET" -> mockTrainerDetailsResponse()
            path.endsWith("/trainers") && method == "GET" -> mockTrainersResponse()
            
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
            "freeze_days_total": 7,
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
    
    private fun mockSubscriptionPlansResponse(): String = """
    [
        {
            "id": "plan-1",
            "name": "Безлимит на месяц",
            "description": "Неограниченное посещение всех групповых занятий в течение месяца",
            "price": 5000.0,
            "duration_days": 30,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Все групповые занятия", "Тренажёрный зал", "Сауна"],
            "is_popular": true
        },
        {
            "id": "plan-2",
            "name": "Безлимит на 3 месяца",
            "description": "Неограниченное посещение всех групповых занятий в течение 3 месяцев",
            "price": 12000.0,
            "duration_days": 90,
            "visits_count": null,
            "type": "unlimited",
            "features": ["Все групповые занятия", "Тренажёрный зал", "Сауна", "Бассейн"],
            "is_popular": false
        },
        {
            "id": "plan-3",
            "name": "8 занятий",
            "description": "8 групповых занятий на выбор",
            "price": 3500.0,
            "duration_days": 30,
            "visits_count": 8,
            "type": "limited",
            "features": ["Групповые занятия на выбор"],
            "is_popular": false
        },
        {
            "id": "plan-4",
            "name": "Персональные тренировки",
            "description": "10 персональных тренировок с тренером",
            "price": 15000.0,
            "duration_days": 90,
            "visits_count": 10,
            "type": "personal",
            "features": ["Персональный тренер", "Индивидуальная программа", "Консультация по питанию"],
            "is_popular": false
        }
    ]
    """.trimIndent()
    
    private fun mockTrainersResponse(): String = """
    [
        {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога, Растяжка", "rating": 4.8},
        {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9},
        {"id": "trainer-3", "name": "Елена Сидорова", "photo_url": null, "specialization": "Кардио, Аэробика", "rating": 4.7},
        {"id": "trainer-4", "name": "Ольга Козлова", "photo_url": null, "specialization": "Пилатес", "rating": 4.6},
        {"id": "trainer-5", "name": "Дмитрий Волков", "photo_url": null, "specialization": "Единоборства, CrossFit", "rating": 4.9}
    ]
    """.trimIndent()
    
    private fun mockTrainerDetailsResponse(): String = """
    {
        "id": "trainer-1",
        "name": "Мария Иванова",
        "photo_url": null,
        "specialization": "Йога, Растяжка",
        "rating": 4.8
    }
    """.trimIndent()
}
