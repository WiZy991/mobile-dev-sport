<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/trainings')]
class TrainingController extends AbstractController
{
    #[Route('', name: 'api_trainings_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        // TODO: заменить на данные из БД (фильтры по дате/типу)
        return $this->json(json_decode($this->mockScheduleResponse(), true));
    }

    #[Route('/{id}', name: 'api_trainings_show', methods: ['GET'])]
    public function show(string $id): JsonResponse
    {
        // TODO: вернуть конкретную тренировку по id из БД
        return $this->json(json_decode($this->mockTrainingDetailsResponse(), true));
    }

    private function mockScheduleResponse(): string
    {
        return <<<'JSON'
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
  }
]
JSON;
    }

    private function mockTrainingDetailsResponse(): string
    {
        return <<<'JSON'
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
JSON;
    }
}

