<?php

namespace App\Controller\Api;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/auth')]
class AuthController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/login', name: 'api_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = trim((string) ($data['email'] ?? ''));
        $password = $data['password'] ?? ''; // пока не проверяем

        $user = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);

        // Если пользователя нет, но это первый вход — создаём (для быстрого старта)
        if (!$user && $email !== '') {
            $count = $this->em->getRepository(User::class)->count([]);
            if ($count === 0) {
                $user = (new User())
                    ->setEmail($email)
                    ->setName((string) ($data['name'] ?? 'Пользователь'))
                    ->setPhone((string) ($data['phone'] ?? '+7 900 000-00-00'))
                    ->setBonusPoints(0);
                $this->em->persist($user);
                $this->em->flush();
            }
        }

        if (!$user) {
            return $this->json(['error' => 'User not found', 'code' => 'invalid_credentials'], 401);
        }

        if ($user->isBlocked()) {
            return $this->json(['error' => 'Access denied', 'code' => 'user_blocked'], 403);
        }

        return $this->json([
            'token' => bin2hex(random_bytes(16)),
            'refresh_token' => bin2hex(random_bytes(16)),
            'user' => $this->serializeUser($user),
        ]);
    }

    #[Route('/register', name: 'api_auth_register', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = trim((string) ($data['email'] ?? ''));
        $name = trim((string) ($data['name'] ?? 'Новый пользователь'));
        $phone = trim((string) ($data['phone'] ?? '+7 900 000-00-00'));

        if ($email === '') {
            return $this->json(['error' => 'Email is required'], 400);
        }

        $existing = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
        if ($existing !== null) {
            return $this->json(['error' => 'User with this email already exists'], 409);
        }

        $user = (new User())
            ->setEmail($email)
            ->setName($name)
            ->setPhone($phone)
            ->setBonusPoints(0)
            ->setIsBlocked(false);

        $this->em->persist($user);
        $this->em->flush();

        return $this->json([
            'token' => bin2hex(random_bytes(16)),
            'refresh_token' => bin2hex(random_bytes(16)),
            'user' => $this->serializeUser($user),
        ]);
    }

    private function serializeUser(User $user): array
    {
        return [
            'id' => 'user-' . $user->getId(),
            'email' => $user->getEmail(),
            'name' => $user->getName(),
            'phone' => $user->getPhone(),
            'avatar_url' => $user->getAvatarUrl(),
            'bonus_points' => $user->getBonusPoints(),
            'created_at' => $user->getCreatedAt()->format('Y-m-d\TH:i:s'),
        ];
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

