<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/trainers')]
class TrainerController extends AbstractController
{
    #[Route('', name: 'api_trainers_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(json_decode($this->mockTrainersResponse(), true));
    }

    #[Route('/{id}', name: 'api_trainers_show', methods: ['GET'])]
    public function show(string $id): JsonResponse
    {
        // TODO: выбрать тренера по id из БД
        return $this->json(json_decode($this->mockTrainerDetailsResponse(), true));
    }

    private function mockTrainersResponse(): string
    {
        return <<<'JSON'
[
  {"id": "trainer-1", "name": "Мария Иванова", "photo_url": null, "specialization": "Йога, Растяжка", "rating": 4.8},
  {"id": "trainer-2", "name": "Алексей Петров", "photo_url": null, "specialization": "Силовой тренинг", "rating": 4.9}
]
JSON;
    }

    private function mockTrainerDetailsResponse(): string
    {
        return <<<'JSON'
{
  "id": "trainer-1",
  "name": "Мария Иванова",
  "photo_url": null,
  "specialization": "Йога, Растяжка",
  "rating": 4.8
}
JSON;
    }
}

