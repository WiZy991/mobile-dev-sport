<?php

namespace App\Controller\Api;

use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
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
        private readonly LeadIngestionService $leadIngestion,
    ) {}

    #[Route('/login', name: 'api_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $password = (string) ($data['password'] ?? '');

        if ($email === '' || $password === '') {
            return $this->json(['error' => 'Укажите email и password', 'code' => 'missing_credentials'], 400);
        }

        $user = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);

        if (!$user) {
            return $this->json(['error' => 'User not found', 'code' => 'invalid_credentials'], 401);
        }
        $hash = $user->getPasswordHash();
        if ($hash === null || $hash === '') {
            return $this->json([
                'error' => 'Для этого аккаунта вход по паролю не настроен',
                'code' => 'password_not_set',
            ], 401);
        }
        if (!password_verify($password, $hash)) {
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
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $name = trim((string) ($data['name'] ?? 'Новый пользователь'));
        $phone = trim((string) ($data['phone'] ?? '+7 900 000-00-00'));
        $password = (string) ($data['password'] ?? '');

        if ($email === '') {
            return $this->json(['error' => 'Укажите email'], 400);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Некорректный email', 'code' => 'invalid_email'], 400);
        }
        if (mb_strlen($password) < 6) {
            return $this->json(['error' => 'Пароль должен быть не менее 6 символов', 'code' => 'weak_password'], 400);
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
            ->setIsBlocked(false)
            ->setPasswordHash(password_hash($password, PASSWORD_BCRYPT));

        $this->mobileClientPayloadApplier->applyRegistrationPayload($user, $data);

        $this->em->persist($user);
        $this->em->flush();

        $referralCode = trim((string) ($data['referral_code'] ?? $data['promo_code'] ?? $data['referralCode'] ?? ''));
        if ($referralCode !== '') {
            try {
                $this->leadIngestion->ingest(
                    $name,
                    $phone,
                    $email,
                    LeadSource::REFERRAL,
                    'Регистрация по рекомендации. Код: ' . $referralCode,
                    $user,
                );
                $this->em->flush();
            } catch (\InvalidArgumentException) {
            }
        } else {
            $this->leadIngestion->attachUserIfOpenLead(
                $phone,
                $user,
                'Клиент зарегистрировался в приложении',
            );
            $this->em->flush();
        }

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

