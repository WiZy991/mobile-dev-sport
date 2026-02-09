<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/auth')]
class AuthController extends AbstractController
{
    #[Route('/login', name: 'api_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        // TODO: заменить на реальную аутентификацию (JWT/пароли из БД)
        $data = json_decode($request->getContent(), true) ?? [];
        $email = $data['email'] ?? 'user@example.com';

        return $this->json([
            'token' => bin2hex(random_bytes(16)),
            'refresh_token' => bin2hex(random_bytes(16)),
            'user' => [
                'id' => 'user-123',
                'email' => $email,
                'name' => 'Антон',
                'phone' => '+7 922 222-22-22',
                'avatar_url' => null,
                'bonus_points' => 150,
                'created_at' => '2025-06-01T10:00:00',
            ],
        ]);
    }

    #[Route('/register', name: 'api_auth_register', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        // TODO: сохранить пользователя в БД
        $data = json_decode($request->getContent(), true) ?? [];

        return $this->json([
            'token' => bin2hex(random_bytes(16)),
            'refresh_token' => bin2hex(random_bytes(16)),
            'user' => [
                'id' => 'user-new-' . substr(bin2hex(random_bytes(4)), 0, 8),
                'email' => $data['email'] ?? 'newuser@example.com',
                'name' => $data['name'] ?? 'Новый пользователь',
                'phone' => $data['phone'] ?? '+7 900 000-00-00',
                'avatar_url' => null,
                'bonus_points' => 0,
                'created_at' => '2026-02-04T22:00:00',
            ],
        ]);
    }

    #[Route('/refresh', name: 'api_auth_refresh', methods: ['POST'])]
    public function refresh(Request $request): JsonResponse
    {
        // TODO: проверить refresh_token и выдать новый access_token
        return $this->json([
            'token' => bin2hex(random_bytes(16)),
            'refresh_token' => bin2hex(random_bytes(16)),
            'user' => [
                'id' => 'user-123',
                'email' => 'user@example.com',
                'name' => 'Антон',
                'phone' => '+7 922 222-22-22',
                'avatar_url' => null,
                'bonus_points' => 150,
                'created_at' => '2025-06-01T10:00:00',
            ],
        ]);
    }

    #[Route('/logout', name: 'api_auth_logout', methods: ['POST'])]
    public function logout(): JsonResponse
    {
        // TODO: инвалидировать токен / refresh_token
        return $this->json(['success' => true]);
    }
}

