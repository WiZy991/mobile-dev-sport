<?php

namespace App\Controller\Api;

use App\Entity\StaffNotification;
use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
use App\Service\MobileClientPayloadApplier;
use App\Service\Staff\StaffEventNotifier;
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
        private readonly StaffEventNotifier $staffEventNotifier,
    ) {}

    #[Route('/login', name: 'api_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $password = (string) ($data['password'] ?? '');

        if ($email === '') {
            return $this->json(['error' => 'Укажите email', 'code' => 'missing_email'], 400);
        }

        $user = $this->findUserByEmail($email);

        if ($password === '') {
            if ($user !== null && $this->userPasswordNotSet($user)) {
                $hint = $this->loginHintForUser($user);

                return $this->json([
                    'error' => $hint['message'],
                    'code' => $hint['code'],
                ], 401);
            }
            return $this->json(['error' => 'Введите пароль', 'code' => 'missing_password'], 400);
        }

        if (!$user) {
            return $this->json(['error' => 'Неверный email или пароль', 'code' => 'invalid_credentials'], 401);
        }
        if ($this->userPasswordNotSet($user)) {
            $hint = $this->loginHintForUser($user);

            return $this->json([
                'error' => $hint['message'],
                'code' => $hint['code'],
            ], 401);
        }
        if (!password_verify($password, (string) $user->getPasswordHash())) {
            return $this->json(['error' => 'Неверный email или пароль', 'code' => 'invalid_credentials'], 401);
        }

        if ($user->isBlocked()) {
            return $this->json(['error' => 'Access denied', 'code' => 'user_blocked'], 403);
        }

        return $this->json($this->mobileTokens->issue($user, false));
    }

    /**
     * Подсказка для экрана входа: есть ли аккаунт и нужен ли пароль.
     * Не раскрывает лишнего — только то, что и так видно при попытке входа.
     */
    #[Route('/login-hint', name: 'api_auth_login_hint', methods: ['POST'])]
    public function loginHint(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));

        if ($email === '') {
            return $this->json(['error' => 'Укажите email', 'code' => 'missing_email'], 400);
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Некорректный email', 'code' => 'invalid_email'], 400);
        }

        $user = $this->findUserByEmail($email);

        return $this->json($this->loginHintForUser($user));
    }

    private function loginHintForUser(?User $user): array
    {
        if ($user === null) {
            return [
                'message' => 'Введите пароль. Если аккаунта ещё нет — пройдите регистрацию.',
                'code' => 'email_unknown',
            ];
        }

        if ($this->userPasswordNotSet($user)) {
            return [
                'message' => $this->isSberLinkedAccount($user)
                    ? $this->passwordNotSetMessage()
                    : 'Аккаунт найден, но пароль для входа по email не задан. Задайте пароль в профиле или обратитесь в клуб.',
                'code' => 'password_not_set',
            ];
        }

        return [
            'message' => 'Введите пароль',
            'code' => 'password_required',
        ];
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

        $existing = $this->findUserByEmail($email);
        if ($existing !== null) {
            $existingHash = $existing->getPasswordHash();
            if ($existingHash === null || $existingHash === '') {
                return $this->json([
                    'error' => 'Этот email уже привязан к аккаунту Сбер ID. Войдите через Сбер ID '
                        . 'и задайте пароль в Профиле → Изменить пароль.',
                    'code' => 'password_not_set',
                ], 409);
            }

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

        $this->staffEventNotifier->notifyBySection(
            'clients',
            StaffNotification::TYPE_CLIENT,
            'Новый клиент в приложении',
            sprintf('%s (%s) зарегистрировался', $user->getName(), $user->getEmail()),
            $user->getId() !== null ? (string) $user->getId() : null,
        );

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

    /** Поиск по email без учёта регистра (Сбер ID может сохранить mixed case). */
    private function findUserByEmail(string $email): ?User
    {
        $normalized = mb_strtolower(trim($email));
        if ($normalized === '') {
            return null;
        }

        return $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('LOWER(u.email) = :email')
            ->setParameter('email', $normalized)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    private function userPasswordNotSet(User $user): bool
    {
        $hash = $user->getPasswordHash();

        return $hash === null || trim((string) $hash) === '';
    }

    private function isSberLinkedAccount(User $user): bool
    {
        $sberId = $user->getSberId();

        return ($sberId !== null && trim($sberId) !== '')
            || $user->getPassportVerificationProvider() === 'sber_id';
    }

    private function passwordNotSetMessage(): string
    {
        return 'Аккаунт с этим email есть, но пароль не задан (вход через Сбер ID). '
            . 'Нажмите «Войти через Сбер ID» ниже, затем Профиль → Изменить пароль — '
            . 'и задайте пароль для входа по email.';
    }
}

