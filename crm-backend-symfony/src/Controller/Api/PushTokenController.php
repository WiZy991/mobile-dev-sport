<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/user')]
class PushTokenController extends AbstractController
{
    #[Route('/push-token', name: 'api_user_push_token', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        // TODO: сохранить токен в БД, связав с текущим пользователем

        return $this->json([
            'success' => true,
            'token' => $data['token'] ?? null,
            'platform' => $data['platform'] ?? 'android',
        ]);
    }
}

