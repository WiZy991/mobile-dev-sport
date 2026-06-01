<?php

namespace App\Controller\Api;

use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\MobileClientPayloadApplier;
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
        private readonly MobileClientPayloadApplier $mobileClientPayloadApplier,
        private readonly MobileAuthTokenIssuer $mobileTokens,
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

        return $this->json($this->mobileTokens->issue($user, false));
    }

    #[Route('/register', name: 'api_auth_register', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = trim((string) ($data['email'] ?? ''));
        $name = trim((string) ($data['name'] ?? 'Новый пользователь'));
        $phone = trim((string) ($data['phone'] ?? '+7 900 000-00-00'));

        if ($email === '') {
            return $this->json(['error' => 'Укажите email'], 400);
        }

        $existing = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
        if ($existing !== null) {
            return $this->json([
                'error' => 'Пользователь с таким email уже зарегистрирован',
                'code' => 'email_already_exists',
            ], 409);
        }

        $user = (new User())
            ->setEmail($email)
            ->setName($name)
            ->setPhone($phone)
            ->setBonusPoints(0)
            ->setIsBlocked(false);

        $this->mobileClientPayloadApplier->applyRegistrationPayload($user, $data);

        $this->em->persist($user);
        $this->em->flush();

        return $this->json($this->mobileTokens->issue($user, true));
    }

    #[Route('/refresh', name: 'api_auth_refresh', methods: ['POST'])]
    public function refresh(Request $request): JsonResponse
    {
        $refresh = $this->extractBearerToken($request);
        if ($refresh === null || $refresh === '') {
            return $this->json(['error' => 'Требуется refresh-токен', 'code' => 'missing_refresh'], 401);
        }

        $user = $this->em->getRepository(User::class)->findOneBy(['apiRefreshToken' => $refresh]);
        if ($user === null || $user->isBlocked()) {
            return $this->json(['error' => 'Недействительный refresh-токен', 'code' => 'invalid_refresh'], 401);
        }

        return $this->json($this->mobileTokens->issue($user, false));
    }

    #[Route('/logout', name: 'api_auth_logout', methods: ['POST'])]
    public function logout(Request $request): JsonResponse
    {
        $user = $this->resolveUserFromAccessOrRefresh($request);
        if ($user !== null) {
            $this->mobileTokens->revokeSession($user);
        }

        return $this->json(['success' => true]);
    }

    private function resolveUserFromAccessOrRefresh(Request $request): ?User
    {
        $bearer = $this->extractBearerToken($request);
        if ($bearer === null || $bearer === '') {
            return null;
        }

        $byAccess = $this->em->getRepository(User::class)->findOneBy(['apiAccessToken' => $bearer]);
        if ($byAccess !== null) {
            return $byAccess;
        }

        return $this->em->getRepository(User::class)->findOneBy(['apiRefreshToken' => $bearer]);
    }

    private function extractBearerToken(Request $request): ?string
    {
        $auth = $request->headers->get('Authorization', '');
        if (preg_match('/Bearer\s+(\S+)/i', $auth, $m)) {
            return trim($m[1], " \t\"'");
        }

        return null;
    }
}

