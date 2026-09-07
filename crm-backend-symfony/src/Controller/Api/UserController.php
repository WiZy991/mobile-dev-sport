<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Sale;
use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\Api\UserAccountDeletionService;
use App\Service\Notification\ClientNotificationService;
use App\Service\CurrentUserResolver;
use App\Service\Auth\EmailVerificationService;
use App\Service\Auth\ProfileLegalLock;
use App\Service\MobileClientPayloadApplier;
use App\Service\Reports\OccupancyService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/user')]
class UserController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly MobileClientPayloadApplier $mobileClientPayloadApplier,
        private readonly MobileAuthTokenIssuer $mobileTokens,
        private readonly UserAccountDeletionService $accountDeletion,
        private readonly OccupancyService $occupancyService,
        private readonly ClientNotificationService $clientNotifications,
        private readonly EmailVerificationService $emailVerification,
        private readonly ProfileLegalLock $profileLegalLock,
    ) {}

    #[Route('/access-status', name: 'api_user_access_status', methods: ['GET'])]
    public function accessStatus(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $snapshot = $this->occupancyService->getUserAccessSnapshot($user, null);

        return $this->json($snapshot);
    }

    #[Route('/stats', name: 'api_user_stats', methods: ['GET'])]
    public function stats(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $visitCount = (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.user = :user')
            ->andWhere('a.eventType = :entry')
            ->andWhere('a.result = :granted')
            ->setParameter('user', $user)
            ->setParameter('entry', 'entry')
            ->setParameter('granted', 'granted')
            ->getQuery()
            ->getSingleScalarResult();

        $streakDays = $this->computeStreak($user);

        $achievements = [];
        if ($visitCount >= 1) {
            $achievements[] = ['id' => 'first_visit', 'name' => 'Первое посещение', 'description' => 'Добро пожаловать в зал!', 'unlocked' => true];
        }
        if ($visitCount >= 5) {
            $achievements[] = ['id' => 'regular', 'name' => 'Регулярный посетитель', 'description' => '5 посещений', 'unlocked' => true];
        }
        if ($visitCount >= 20) {
            $achievements[] = ['id' => 'enthusiast', 'name' => 'Энтузиаст', 'description' => '20 посещений', 'unlocked' => true];
        }
        if ($visitCount >= 50) {
            $achievements[] = ['id' => 'veteran', 'name' => 'Ветеран', 'description' => '50 посещений', 'unlocked' => true];
        }
        if ($streakDays >= 3) {
            $achievements[] = ['id' => 'streak_3', 'name' => 'Серия 3 дня', 'description' => '3 дня подряд', 'unlocked' => true];
        }
        if ($streakDays >= 7) {
            $achievements[] = ['id' => 'streak_7', 'name' => 'Недельная серия', 'description' => '7 дней подряд', 'unlocked' => true];
        }

        return $this->json([
            'total_visits' => $visitCount,
            'streak_days' => $streakDays,
            'achievements' => $achievements,
        ]);
    }

    #[Route('/purchases', name: 'api_user_purchases', methods: ['GET'])]
    public function purchases(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $sales = $this->em->getRepository(Sale::class)->findBy(
            ['user' => $user],
            ['createdAt' => 'DESC'],
            100
        );

        $list = array_map(static function (Sale $s) {
            return [
                'id' => (string) $s->getId(),
                'product_name' => $s->getProductName(),
                'quantity' => $s->getQuantity(),
                'price' => $s->getPrice(),
                'total' => $s->getTotal(),
                'payment_method' => $s->getPaymentMethod(),
                'created_at' => $s->getCreatedAt()->format('Y-m-d\TH:i:s'),
                'club_name' => $s->getSubscription()?->getClub()?->getName()
                    ?? $s->getUser()?->getClub()?->getName(),
            ];
        }, $sales);

        return $this->json($list);
    }

    #[Route('/profile', name: 'api_user_profile_get', methods: ['GET'])]
    public function profile(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        return $this->json($this->serializeUserProfile($user));
    }

    #[Route('/profile', name: 'api_user_profile_update', methods: ['PUT'])]
    public function updateProfile(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];

        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        if (isset($data['email'])) {
            $incoming = mb_strtolower(trim((string) $data['email']));
            if ($incoming !== '' && $incoming !== mb_strtolower($user->getEmail())) {
                $user->setEmail($incoming);
                $user->setEmailVerifiedAt(null);
            }
        }
        if (isset($data['name'])) {
            $incoming = trim((string) $data['name']);
            $current = trim($user->getName());
            if ($incoming !== '' && $incoming !== $current) {
                if ($this->profileLegalLock->isLocked($user) && $this->profileLegalLock->blocksFilledIdentityChange($current, $incoming)) {
                    return $this->json([
                        'error' => 'На ваши данные приобретён активный абонемент. Если данные изменились, свяжитесь со службой поддержки.',
                        'code' => 'profile_locked',
                    ], 403);
                }
                $user->setName($incoming);
            }
        }
        if (isset($data['phone'])) {
            $incoming = trim((string) $data['phone']);
            $current = trim((string) ($user->getPhone() ?? ''));
            $incomingDigits = preg_replace('/\D+/', '', $incoming) ?? '';
            $currentDigits = preg_replace('/\D+/', '', $current) ?? '';
            if ($incoming !== '' && $incoming !== $current && $incomingDigits !== $currentDigits) {
                if ($this->profileLegalLock->isLocked($user) && $this->profileLegalLock->blocksFilledIdentityChange($currentDigits, $incomingDigits)) {
                    return $this->json([
                        'error' => 'На ваши данные приобретён активный абонемент. Если данные изменились, свяжитесь со службой поддержки.',
                        'code' => 'profile_locked',
                    ], 403);
                }
                $user->setPhone($incoming);
            }
        }

        try {
            $this->mobileClientPayloadApplier->applyProfilePatch($user, $data);
        } catch (\DomainException $e) {
            if ($e->getMessage() === 'passport_locked' || $e->getMessage() === 'profile_locked') {
                return $this->json([
                    'error' => 'На ваши данные приобретён активный абонемент. Если данные изменились, свяжитесь со службой поддержки.',
                    'code' => 'profile_locked',
                ], 403);
            }
            if ($e->getMessage() === 'club_not_found') {
                return $this->json([
                    'error' => 'Выбранный зал не найден в CRM. Обновите приложение или выберите зал заново.',
                    'code' => 'club_not_found',
                ], 400);
            }
            throw $e;
        }

        $this->em->persist($user);
        $this->em->flush();

        return $this->json(array_merge($this->serializeUserProfile($user), [
            'profile_locked' => $this->profileLegalLock->isLocked($user),
        ]));
    }

    #[Route('/email/resend', name: 'api_user_email_resend', methods: ['POST'])]
    public function resendEmailVerification(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }
        if ($user->isEmailVerified()) {
            return $this->json(['ok' => true, 'already_verified' => true, 'email' => $user->getEmail()]);
        }
        $this->emailVerification->sendConfirmation($user);

        return $this->json(['ok' => true, 'email' => $user->getEmail()]);
    }

    #[Route('/change-password', name: 'api_user_change_password', methods: ['POST'])]
    public function changePassword(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];

        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $currentPassword = (string) ($data['current_password'] ?? '');
        $newPassword = (string) ($data['new_password'] ?? '');

        if (mb_strlen($newPassword) < 6) {
            return $this->json([
                'error' => 'Пароль должен быть не менее 6 символов',
                'code' => 'weak_password',
            ], 400);
        }

        $hash = $user->getPasswordHash();
        if ($hash !== null && $hash !== '') {
            if ($currentPassword === '' || !password_verify($currentPassword, $hash)) {
                return $this->json([
                    'error' => 'Неверный текущий пароль',
                    'code' => 'invalid_current_password',
                ], 401);
            }
        }

        if ($hash !== null && $hash !== '' && password_verify($newPassword, $hash)) {
            return $this->json([
                'error' => 'Новый пароль должен отличаться от текущего',
                'code' => 'same_password',
            ], 400);
        }

        $user->setPasswordHash(password_hash($newPassword, PASSWORD_BCRYPT));
        $this->em->persist($user);
        $this->em->flush();

        return $this->json(['success' => true]);
    }

    #[Route('/account', name: 'api_user_account_delete', methods: ['DELETE'])]
    public function deleteAccount(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $this->accountDeletion->deleteAccount($user);

        return $this->json(['success' => true]);
    }

    #[Route('/notification-settings', name: 'api_user_notification_settings_get', methods: ['GET'])]
    public function notificationSettings(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        return $this->json(ClientNotificationService::serializeSettings($user));
    }

    #[Route('/notification-settings', name: 'api_user_notification_settings_update', methods: ['PUT'])]
    public function updateNotificationSettings(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $this->clientNotifications->applySettings($user, $data);
        $this->em->persist($user);
        $this->em->flush();

        return $this->json(ClientNotificationService::serializeSettings($user));
    }

    /** @return array<string, mixed> */
    private function serializeUserProfile(User $user): array
    {
        return array_merge($this->mobileTokens->userArray($user), [
            'gender' => $user->getGender(),
            'passport_series' => $user->getPassportSeries(),
            'passport_number' => $user->getPassportNumber(),
            'passport_issued_by' => $user->getPassportIssuedBy(),
            'passport_issue_date' => $user->getPassportIssueDate()?->format('Y-m-d'),
            'registration_address' => $user->getRegistrationAddress(),
            'profile_locked' => $this->profileLegalLock->isLocked($user),
        ]);
    }

    private function computeStreak(User $user): int
    {
        $dates = $this->em->getConnection()->fetchFirstColumn(
            'SELECT DISTINCT DATE(created_at) AS d
             FROM access_logs
             WHERE user_id = :uid
               AND event_type = \'entry\'
               AND result = \'granted\'
             ORDER BY d DESC
             LIMIT 60',
            ['uid' => $user->getId()],
        );
        $dates = array_values(array_map('strval', $dates));

        if ($dates === []) {
            return 0;
        }

        $today = (new \DateTimeImmutable('today'))->format('Y-m-d');
        $yesterday = (new \DateTimeImmutable('yesterday'))->format('Y-m-d');

        $firstDate = $dates[0];
        if ($firstDate !== $today && $firstDate !== $yesterday) {
            return 0;
        }

        $streak = 0;
        $expected = $firstDate;
        foreach ($dates as $d) {
            if ($d !== $expected) {
                break;
            }
            ++$streak;
            $expected = (new \DateTimeImmutable($d . ' -1 day'))->format('Y-m-d');
        }

        return $streak;
    }
}

