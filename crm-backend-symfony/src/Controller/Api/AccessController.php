<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\GuestPass;
use App\Entity\StaffUser;
use App\Entity\User;
use App\Service\Integration\FitnessClubEntryQrTimestamp;
use App\Service\Integration\PercoWebClient;
use App\Service\Integration\SubscriptionGateResolver;
use App\Service\Api\SubscriptionLifecycleService;
use App\Service\Reports\OccupancyService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/access')]
class AccessController extends AbstractController
{
    /** Эхо PERCo / двойной скан того же QR — не писать второй лог. */
    private const QR_DEDUPE_SECONDS = 90;
    /** Сразу после входа не считать повторный скан выходом (эхо считывателя). */
    private const EXIT_GRACE_SECONDS = 45;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly PercoWebClient $percoWebClient,
        private readonly SubscriptionGateResolver $subscriptionGateResolver,
        private readonly OccupancyService $occupancyService,
        private readonly SubscriptionLifecycleService $subscriptionLifecycle,
        private readonly string $accessGateToken = '',
        private readonly string $percoOpenFromCrm = '1',
    ) {}

    #[Route('/entry', name: 'api_access_entry', methods: ['POST'])]
    public function entry(Request $request): JsonResponse
    {
        if ($g = $this->requireAccessGate($request)) {
            return $g;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $qr = (string) ($data['qr'] ?? '');
        $deviceId = $data['device_id'] ?? null;

        $gateClub = $this->resolveDefaultClub();

        $log = new AccessLog();
        $log->setRawData($qr)
            ->setDeviceId($deviceId)
            ->setResult('denied')
            ->setClub($gateClub);

        $response = [
            'access_granted' => false,
            'reason' => 'unknown_error',
        ];

        // Проверяем формат QR
        $parts = explode(':', $qr);
        if (count($parts) < 4 || $parts[0] !== 'FITNESSCLUB') {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        // Гостевой пропуск: FITNESSCLUB:GUEST:passId:token
        if ($parts[1] === 'GUEST') {
            return $this->handleGuestPassEntry($qr, $parts, $log, $deviceId, $response);
        }

        // Тренер: FITNESSCLUB:STAFF:staffId:timestamp
        if ($parts[1] === 'STAFF') {
            return $this->handleStaffEntry($parts, $log, $response);
        }

        // Обычный вход: FITNESSCLUB:ENTRY:user-123:timestamp
        if ($parts[1] !== 'ENTRY' || count($parts) !== 4) {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        $userExternalId = $parts[2]; // например, user-123 или 123 (короткий сегмент для PERCo)
        $timestamp = FitnessClubEntryQrTimestamp::parseToUnixMs($parts[3]);
        if ($timestamp === null) {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        // Находим пользователя по внешнему ID user-123 -> 123
        $userId = null;
        if (str_starts_with($userExternalId, 'user-')) {
            $userId = (int) substr($userExternalId, 5);
        } else {
            $userId = (int) $userExternalId;
        }

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->find($userId);
        if (!$user) {
            $log->setReason('user_not_found');
            $response['reason'] = 'user_not_found';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 404);
        }

        $log->setUser($user);

        if ($user->isBlocked()) {
            $log->setReason('user_blocked');
            $response['reason'] = 'user_blocked';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        // Тот же QR уже прошёл (скан + эхо PERCo) — не плодим entry/exit и не списываем посещение.
        $dup = $this->occupancyService->findRecentGrantedByRawQr($qr, self::QR_DEDUPE_SECONDS);
        if ($dup !== null) {
            $passage = ($dup['event_type'] === 'exit') ? 'exit' : 'entry';
            $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

            return $this->json($this->mergeEntrySuccess(
                [
                    'access_granted' => true,
                    'reason' => 'ok',
                    'passage' => $passage,
                    'duplicate' => true,
                    'success' => true,
                    'user' => [
                        'id' => 'user-' . $user->getId(),
                        'name' => $user->getName(),
                        'phone' => $user->getPhone(),
                    ],
                ],
                $percoUnlock,
            ));
        }

        // Повторный скан = выход. Grace после входа — защита от мгновенного exit из эха.
        if ($this->occupancyService->isUserCurrentlyInside($user, null)) {
            $sinceEntry = $this->occupancyService->secondsSinceLastGrantedEntry($user, null);
            if ($sinceEntry !== null && $sinceEntry < self::EXIT_GRACE_SECONDS) {
                $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

                return $this->json($this->mergeEntrySuccess(
                    [
                        'access_granted' => true,
                        'reason' => 'ok',
                        'passage' => 'entry',
                        'duplicate' => true,
                        'success' => true,
                        'user' => [
                            'id' => 'user-' . $user->getId(),
                            'name' => $user->getName(),
                            'phone' => $user->getPhone(),
                        ],
                    ],
                    $percoUnlock,
                ));
            }

            $log->setEventType('exit')
                ->setResult('granted')
                ->setReason('ok');
            $this->em->persist($log);
            $this->em->flush();
            $this->occupancyService->notifyPresenceChanged($gateClub);

            $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

            return $this->json($this->mergeEntrySuccess(
                [
                    'access_granted' => true,
                    'reason' => 'ok',
                    'passage' => 'exit',
                    'success' => true,
                    'user' => [
                        'id' => 'user-' . $user->getId(),
                        'name' => $user->getName(),
                        'phone' => $user->getPhone(),
                    ],
                ],
                $percoUnlock,
            ));
        }

        // Проверка времени (15 секунд — только для входа)
        $nowMs = (int) (microtime(true) * 1000);
        if (abs($nowMs - $timestamp) > 15 * 1000) {
            $log->setReason('qr_expired');
            $response['reason'] = 'qr_expired';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        // Проверяем наличие активного абонемента (календарь + клуб при известном контексте клуба)
        [$activeSub, $deny] = $this->subscriptionGateResolver->resolveForEntry($user, $gateClub);

        if (!$activeSub) {
            $reason = match ($deny) {
                'wrong_club' => 'subscription_wrong_club',
                'visits_exhausted' => 'visits_exhausted',
                default => 'no_active_subscription',
            };
            $log->setReason($reason);
            $response['reason'] = $reason;
            $response['message'] = match ($reason) {
                'visits_exhausted' => 'Лимит посещений по абонементу исчерпан',
                'subscription_wrong_club' => 'Абонемент оформлен на другой клуб',
                default => 'Нет действующего абонемента',
            };
            if ($deny === 'wrong_club' && $gateClub !== null) {
                $response['club_id'] = $gateClub->getId();
            }
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        // Доступ разрешён — атомарно списываем посещение (не уходит в минус).
        if (!$this->subscriptionLifecycle->consumeVisitForEntry($activeSub)) {
            $log->setReason('visits_exhausted');
            $response['reason'] = 'visits_exhausted';
            $response['message'] = 'Лимит посещений по абонементу исчерпан';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        $log->setEventType('entry')
            ->setResult('granted')
            ->setReason('ok');

        $this->em->persist($log);
        $this->em->flush();
        $this->occupancyService->notifyPresenceChanged($gateClub);

        $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

        return $this->json($this->mergeEntrySuccess(
            [
                'access_granted' => true,
                'reason' => 'ok',
                'passage' => 'entry',
                'user' => [
                    'id' => 'user-' . $user->getId(),
                    'name' => $user->getName(),
                    'phone' => $user->getPhone(),
                ],
            ],
            $percoUnlock,
        ));
    }

    #[Route('/exit', name: 'api_access_exit', methods: ['POST'])]
    public function exit(Request $request): JsonResponse
    {
        if ($g = $this->requireAccessGate($request)) {
            return $g;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $qr = (string) ($data['qr'] ?? '');
        $deviceId = $data['device_id'] ?? null;

        $log = new AccessLog();
        $log->setRawData($qr)
            ->setDeviceId($deviceId)
            ->setEventType('exit')
            ->setResult('granted')
            ->setReason('ok')
            ->setClub($this->resolveDefaultClub());

        $parts = explode(':', $qr);
        if (count($parts) >= 3 && $parts[0] === 'FITNESSCLUB' && $parts[1] === 'ENTRY') {
            $userExternalId = $parts[2];
            $userId = str_starts_with($userExternalId, 'user-') ? (int) substr($userExternalId, 5) : (int) $userExternalId;
            $user = $this->em->getRepository(User::class)->find($userId);
            if ($user) {
                $log->setUser($user);
            }
        }

        $this->em->persist($log);
        $this->em->flush();
        $this->occupancyService->notifyPresenceChanged(null);

        return $this->json(['success' => true]);
    }

    /**
     * @param string[] $parts
     * @param array<string, mixed> $response
     */
    private function handleStaffEntry(array $parts, AccessLog $log, array $response): JsonResponse
    {
        if (count($parts) !== 4) {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        $staffId = (int) $parts[2];
        $timestamp = FitnessClubEntryQrTimestamp::parseToUnixMs($parts[3]);
        if ($staffId <= 0 || $timestamp === null) {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        /** @var StaffUser|null $staff */
        $staff = $this->em->getRepository(StaffUser::class)->find($staffId);
        if (!$staff instanceof StaffUser) {
            $log->setReason('staff_not_found');
            $response['reason'] = 'staff_not_found';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 404);
        }

        $nowMs = (int) (microtime(true) * 1000);
        if (abs($nowMs - $timestamp) > 15_000) {
            $log->setReason('qr_expired');
            $response['reason'] = 'qr_expired';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        if ($staff->getRegistrationStatus() !== StaffUser::REGISTRATION_APPROVED) {
            $log->setReason('staff_not_approved');
            $response['reason'] = 'staff_not_approved';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }
        if ($staff->requiresTrainerRental() && !$staff->hasValidRental()) {
            $log->setReason('staff_rental_expired');
            $response['reason'] = 'staff_rental_expired';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        $log->setResult('granted')->setReason('ok');
        $this->em->persist($log);
        $this->em->flush();
        $this->occupancyService->notifyPresenceChanged(null);

        $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

        return $this->json($this->mergeEntrySuccess(
            [
                'access_granted' => true,
                'reason' => 'ok',
                'passage' => 'entry',
                'success' => true,
                'user' => [
                    'id' => 'staff-' . $staff->getId(),
                    'name' => $staff->getName() !== '' ? $staff->getName() : $staff->getEmail(),
                    'phone' => $staff->getTrainer()?->getPhone(),
                ],
            ],
            $percoUnlock,
        ));
    }

    private function handleGuestPassEntry(string $qr, array $parts, AccessLog $log, ?string $deviceId, array $response): JsonResponse
    {
        $passId = (int) $parts[2];
        $token = $parts[3] ?? '';

        /** @var GuestPass|null $guestPass */
        $guestPass = $this->em->getRepository(GuestPass::class)->find($passId);
        if (!$guestPass || !$guestPass->isActive() || $guestPass->getQrToken() !== $token) {
            $log->setReason('guest_pass_invalid');
            $response['reason'] = 'guest_pass_invalid';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        $owner = $guestPass->getOwner();
        $log->setUser($owner)
            ->setEventType('entry')
            ->setResult('granted')
            ->setReason('ok');

        $guestPass->setStatus(GuestPass::STATUS_USED)
            ->setUsedAt(new \DateTimeImmutable());

        $this->em->persist($log);
        $this->em->persist($guestPass);
        $this->em->flush();
        $this->occupancyService->notifyPresenceChanged(null);

        $percoUnlock = $this->percoWebClient->tryOpenEntryAfterGranted();

        return $this->json($this->mergeEntrySuccess(
            [
                'access_granted' => true,
                'reason' => 'ok',
                'user' => [
                    'id' => 'guest-' . $guestPass->getId(),
                    'name' => $guestPass->getGuestName() ?: ('Гость ' . $owner->getName()),
                    'phone' => $owner->getPhone(),
                ],
            ],
            $percoUnlock,
        ));
    }

    /**
     * Для legacy-эндпоинта /api/v1/access/entry клуб не передаётся явно.
     * Если в системе ровно один клуб — берём его (типичный single-tenant).
     * В мульти-клубной франшизе клиенты должны идти через /api/v1/gateway/access/entry,
     * где клуб определяется по Bearer-токену шлюза.
     */
    private function resolveDefaultClub(): ?Club
    {
        $repo = $this->em->getRepository(Club::class);
        if ((int) $repo->count([]) !== 1) {
            return null;
        }
        $clubs = $repo->findBy([], null, 1);

        return $clubs[0] ?? null;
    }

    private function requireAccessGate(Request $request): ?JsonResponse
    {
        $expected = trim($this->accessGateToken);
        if ($expected === '') {
            return null;
        }

        $h = (string) $request->headers->get('X-Access-Gate-Token', '');
        if ($h !== '' && hash_equals($expected, $h)) {
            return null;
        }

        $auth = (string) $request->headers->get('Authorization', '');
        if (str_starts_with($auth, 'Bearer ') && hash_equals($expected, trim(substr($auth, 7)))) {
            return null;
        }

        return $this->json([
            'access_granted' => false,
            'reason' => 'unauthorized',
            'message' => 'Нужен заголовок X-Access-Gate-Token (или Authorization: Bearer) с токеном, заданным в ACCESS_GATE_TOKEN на сервере CRM.',
        ], 401);
    }

    /**
     * @param  array<string, mixed>  $data
     * @return array<string, mixed>
     */
    private function mergeEntrySuccess(array $data, ?bool $percoUnlock): array
    {
        $m = $data;
        if ($percoUnlock !== null) {
            $m['perco_unlock'] = $percoUnlock;
        }
        $m['integration'] = [
            'perco_open_from_crm' => $this->percoOpenFromCrm !== '0',
            /** true = открытие двери с ПК/RPi в клубе (скрипт turnstile_gateway) */
            'turnstile_open_locally' => $this->percoOpenFromCrm === '0',
        ];

        return $m;
    }
}

