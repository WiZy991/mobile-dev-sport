<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/subscriptions')]
class SubscriptionController extends AbstractController
{
    #[Route('', name: 'api_subscriptions_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(json_decode($this->mockSubscriptionsResponse(), true));
    }

    #[Route('/plans', name: 'api_subscriptions_plans', methods: ['GET'])]
    public function plans(): JsonResponse
    {
        return $this->json(json_decode($this->mockSubscriptionPlansResponse(), true));
    }

    #[Route('/{id}/freeze', name: 'api_subscriptions_freeze', methods: ['POST'])]
    public function freeze(string $id, Request $request): JsonResponse
    {
        // TODO: учесть days из query и обновить в БД
        return $this->json(json_decode($this->mockFreezeResponse(), true));
    }

    #[Route('/{id}/unfreeze', name: 'api_subscriptions_unfreeze', methods: ['POST'])]
    public function unfreeze(string $id): JsonResponse
    {
        return $this->json(json_decode($this->mockUnfreezeResponse(), true));
    }

    private function mockSubscriptionsResponse(): string
    {
        return <<<'JSON'
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
  }
]
JSON;
    }

    private function mockFreezeResponse(): string
    {
        return <<<'JSON'
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
JSON;
    }

    private function mockUnfreezeResponse(): string
    {
        return <<<'JSON'
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
JSON;
    }

    private function mockSubscriptionPlansResponse(): string
    {
        return <<<'JSON'
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
  }
]
JSON;
    }
}

