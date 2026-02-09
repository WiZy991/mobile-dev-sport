<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1')]
class BookingController extends AbstractController
{
    #[Route('/bookings', name: 'api_bookings_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(json_decode($this->mockBookingsResponse(), true));
    }

    #[Route('/trainings/{id}/book', name: 'api_bookings_book', methods: ['POST'])]
    public function book(string $id): JsonResponse
    {
        return $this->json(json_decode($this->mockBookingResponse(), true));
    }

    #[Route('/trainings/{id}/waiting-list', name: 'api_bookings_waiting_list', methods: ['POST'])]
    public function waitingList(string $id): JsonResponse
    {
        return $this->json(json_decode($this->mockBookingResponse(), true));
    }

    #[Route('/bookings/{id}', name: 'api_bookings_cancel', methods: ['DELETE'])]
    public function cancel(string $id): JsonResponse
    {
        // TODO: пометить бронь отменённой в БД
        return $this->json(['success' => true]);
    }

    private function mockBookingResponse(): string
    {
        return <<<'JSON'
{
  "id": "booking-1",
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
JSON;
    }

    private function mockBookingsResponse(): string
    {
        return <<<'JSON'
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
  }
]
JSON;
    }
}

