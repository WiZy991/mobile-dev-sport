<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\GatewayCommand;
use App\Entity\GuestPass;
use App\Entity\Subscription;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

/**
 * Точки входа для ПК-шлюза, который ставится В КАЖДОМ КЛУБЕ (франшиза).
 *
 * Авторизация: Authorization: Bearer <Club.gatewayToken>
 *
 * Архитектура:
 *  1. Шлюз получает QR (с приложения / считывателя) → POST /api/v1/gateway/access/entry.
 *     CRM валидирует QR, проверяет абонемент, пишет AccessLog, возвращает open_device — параметры
 *     команды для локального PERCo. Шлюз сам шлёт команду в LAN PERCo-Web (см. perco_client.py).
 *  2. Шлюз держит постоянный long-poll GET /api/v1/gateway/commands и выполняет приходящие команды
 *     (в т.ч. «Открыть дверь» из админки CRM), затем подтверждает /commands/{id}/ack.
 *  3. Шлюз шлёт heartbeat — POST /api/v1/gateway/heartbeat — для мониторинга связности.
 *
 * Зачем такая схема: облачная CRM не имеет (и не должна иметь) сетевого доступа в LAN
 * каждого клуба. Локальный шлюз снимает требования к VPN и публикации PERCo наружу.
 */
#[Route('/api/v1/gateway')]
class GatewayController extends AbstractController
{
    private const LONG_POLL_SECONDS = 25.0;
    private const POLL_INTERVAL_US = 1_500_000;
    private const COMMAND_BATCH_LIMIT = 5;

    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('/access/entry', name: 'api_gateway_access_entry', methods: ['POST'])]
    public function entry(Request $request): JsonResponse
    {
        $club = $this->authenticateClub($request);
        if ($club instanceof JsonResponse) {
            return $club;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $qr = (string) ($data['qr'] ?? '');
        $deviceId = isset($data['device_id']) ? (string) $data['device_id'] : ('club-' . $club->getId());

        $log = (new AccessLog())
            ->setRawData($qr)
            ->setDeviceId($deviceId)
            ->setEventType('entry')
            ->setResult('denied')
            ->setClub($club);

        $parts = explode(':', $qr);
        if (count($parts) < 4 || $parts[0] !== 'FITNESSCLUB') {
            return $this->denied($log, 'invalid_format', 400);
        }

        if ($parts[1] === 'GUEST') {
            return $this->handleGuestPassEntry($club, $parts, $log);
        }

        if ($parts[1] !== 'ENTRY' || count($parts) !== 4) {
            return $this->denied($log, 'invalid_format', 400);
        }

        $userExternalId = $parts[2];
        $timestamp = (int) $parts[3];

        // Окно валидности QR — 15 секунд (синхронно с мобильным приложением).
        $nowMs = (int) (microtime(true) * 1000);
        $deltaMs = abs($nowMs - $timestamp);
        if ($deltaMs > 15_000) {
            return $this->denied($log, 'qr_expired', 400, [
                'delta_ms' => $deltaMs,
                'server_now_ms' => $nowMs,
                'qr_timestamp_ms' => $timestamp,
            ]);
        }

        $userId = str_starts_with($userExternalId, 'user-')
            ? (int) substr($userExternalId, 5)
            : (int) $userExternalId;

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->find($userId);
        if (!$user) {
            return $this->denied($log, 'user_not_found', 404);
        }

        $log->setUser($user);

        if ($user->isBlocked()) {
            return $this->denied($log, 'user_blocked', 403);
        }

        $activeSub = $this->findActiveSubscription($user);
        if (!$activeSub) {
            return $this->denied($log, 'no_active_subscription', 403);
        }

        if ($activeSub->getVisitsTotal() !== null) {
            $used = (int) ($activeSub->getVisitsUsed() ?? 0);
            $activeSub->setVisitsUsed($used + 1);
            $this->em->persist($activeSub);
        }

        $log->setResult('granted')->setReason('ok');
        $this->em->persist($log);
        $this->em->flush();

        return $this->json($this->grantedPayload($club, [
            'access_granted' => true,
            'reason' => 'ok',
            'user' => [
                'id' => 'user-' . $user->getId(),
                'name' => $user->getName(),
                'phone' => $user->getPhone(),
            ],
        ]));
    }

    #[Route('/heartbeat', name: 'api_gateway_heartbeat', methods: ['POST'])]
    public function heartbeat(Request $request): JsonResponse
    {
        $club = $this->authenticateClub($request);
        if ($club instanceof JsonResponse) {
            return $club;
        }

        $club->setGatewayLastSeenAt(new \DateTime());
        $this->em->flush();

        $body = json_decode($request->getContent(), true);
        $payload = is_array($body) ? $body : [];

        return $this->json([
            'ok' => true,
            'club_id' => $club->getId(),
            'club_name' => $club->getName(),
            'server_time' => (new \DateTimeImmutable())->format(DATE_ATOM),
            'received' => array_intersect_key($payload, array_flip(['version', 'os', 'perco_status'])),
        ]);
    }

    #[Route('/commands', name: 'api_gateway_commands_poll', methods: ['GET'])]
    public function poll(Request $request): JsonResponse
    {
        $club = $this->authenticateClub($request);
        if ($club instanceof JsonResponse) {
            return $club;
        }

        $club->setGatewayLastSeenAt(new \DateTime());
        $this->em->flush();

        $deadline = microtime(true) + self::LONG_POLL_SECONDS;
        do {
            $commands = $this->fetchPendingCommands($club);
            if ($commands !== []) {
                break;
            }
            if (microtime(true) >= $deadline) {
                break;
            }
            // ignoreUserAbort=false — корректно завершаем цикл, если клиент ушёл.
            if (connection_aborted()) {
                return new JsonResponse(['commands' => []]);
            }
            usleep(self::POLL_INTERVAL_US);
        } while (true);

        $now = new \DateTimeImmutable();
        $payload = [];
        foreach ($commands ?? [] as $cmd) {
            $cmd->setStatus(GatewayCommand::STATUS_DELIVERED)->setDeliveredAt($now);
            $payload[] = $this->serializeCommand($cmd);
        }
        if ($commands) {
            $this->em->flush();
        }

        return $this->json([
            'club_id' => $club->getId(),
            'commands' => $payload,
            'server_time' => $now->format(DATE_ATOM),
        ]);
    }

    #[Route('/commands/{id}/ack', name: 'api_gateway_commands_ack', methods: ['POST'])]
    public function ack(int $id, Request $request): JsonResponse
    {
        $club = $this->authenticateClub($request);
        if ($club instanceof JsonResponse) {
            return $club;
        }

        $cmd = $this->em->getRepository(GatewayCommand::class)->find($id);
        if (!$cmd || $cmd->getClub()->getId() !== $club->getId()) {
            return $this->json(['ok' => false, 'reason' => 'not_found'], 404);
        }

        $body = json_decode($request->getContent(), true) ?? [];
        $statusInput = (string) ($body['status'] ?? GatewayCommand::STATUS_DONE);
        $status = match ($statusInput) {
            GatewayCommand::STATUS_FAILED => GatewayCommand::STATUS_FAILED,
            default => GatewayCommand::STATUS_DONE,
        };

        $cmd->setStatus($status)
            ->setDoneAt(new \DateTimeImmutable())
            ->setResult(is_array($body['result'] ?? null) ? $body['result'] : []);
        $this->em->flush();

        return $this->json(['ok' => true, 'id' => $cmd->getId(), 'status' => $cmd->getStatus()]);
    }

    private function authenticateClub(Request $request): Club|JsonResponse
    {
        $token = $this->extractBearer($request);
        if ($token === '') {
            return $this->json(['ok' => false, 'reason' => 'unauthorized', 'message' => 'Authorization: Bearer <gateway_token>'], 401);
        }

        $club = $this->em->getRepository(Club::class)->findOneBy(['gatewayToken' => $token]);
        if (!$club instanceof Club) {
            return $this->json(['ok' => false, 'reason' => 'unauthorized'], 401);
        }

        return $club;
    }

    private function extractBearer(Request $request): string
    {
        $auth = (string) $request->headers->get('Authorization', '');
        if (str_starts_with($auth, 'Bearer ')) {
            return trim(substr($auth, 7));
        }

        return trim((string) $request->headers->get('X-Gateway-Token', ''));
    }

    private function findActiveSubscription(User $user): ?Subscription
    {
        $today = new \DateTimeImmutable('today');
        /** @var Subscription[] $subs */
        $subs = $this->em->getRepository(Subscription::class)
            ->findBy(['user' => $user, 'status' => 'active']);

        foreach ($subs as $sub) {
            $start = $sub->getStartDate();
            $end = $sub->getEndDate();
            if ($today < $start) {
                continue;
            }
            if ($end !== null && $today > $end) {
                continue;
            }

            return $sub;
        }

        return null;
    }

    /**
     * @param string[] $parts
     */
    private function handleGuestPassEntry(Club $club, array $parts, AccessLog $log): JsonResponse
    {
        $passId = (int) $parts[2];
        $token = $parts[3] ?? '';

        /** @var GuestPass|null $guestPass */
        $guestPass = $this->em->getRepository(GuestPass::class)->find($passId);
        if (!$guestPass || !$guestPass->isActive() || $guestPass->getQrToken() !== $token) {
            return $this->denied($log, 'guest_pass_invalid', 400);
        }

        $owner = $guestPass->getOwner();
        $log->setUser($owner)->setResult('granted')->setReason('ok')->setClub($club);

        $guestPass->setStatus(GuestPass::STATUS_USED)
            ->setUsedAt(new \DateTimeImmutable());

        $this->em->persist($log);
        $this->em->persist($guestPass);
        $this->em->flush();

        return $this->json($this->grantedPayload($club, [
            'access_granted' => true,
            'reason' => 'ok',
            'user' => [
                'id' => 'guest-' . $guestPass->getId(),
                'name' => $guestPass->getGuestName() ?: ('Гость ' . $owner->getName()),
                'phone' => $owner->getPhone(),
            ],
        ]));
    }

    /**
     * @param  array<string, mixed>  $extra  Доп. поля в JSON (например отладка qr_expired для шлюза).
     */
    private function denied(AccessLog $log, string $reason, int $status, array $extra = []): JsonResponse
    {
        $log->setReason($reason);
        $this->em->persist($log);
        $this->em->flush();

        return $this->json(array_merge([
            'access_granted' => false,
            'reason' => $reason,
        ], $extra), $status);
    }

    /**
     * @param  array<string, mixed> $base
     * @return array<string, mixed>
     */
    private function grantedPayload(Club $club, array $base): array
    {
        $deviceId = $club->getPercoEntryDeviceId();
        if ($deviceId !== null && $deviceId > 0) {
            // Команда «открыть» в формате PERCo-Web: cmdNumber/cmdType/cmdValue/cmdParam.
            // Конкретные значения зависят от модели контроллера; передаём дефолты, шлюз
            // может перекрыть их через свою конфигурацию (см. config.ini).
            $base['open_device'] = [
                'device_id' => $deviceId,
                'cmd_number' => 1,
                'cmd_type' => 1,
                'cmd_value' => 1,
                'cmd_param' => 0,
            ];
        }

        return $base;
    }

    /**
     * @return GatewayCommand[]
     */
    private function fetchPendingCommands(Club $club): array
    {
        $this->em->clear(GatewayCommand::class);

        return $this->em->getRepository(GatewayCommand::class)
            ->createQueryBuilder('c')
            ->andWhere('c.club = :club')
            ->andWhere('c.status = :s')
            ->setParameter('club', $club)
            ->setParameter('s', GatewayCommand::STATUS_PENDING)
            ->orderBy('c.id', 'ASC')
            ->setMaxResults(self::COMMAND_BATCH_LIMIT)
            ->getQuery()
            ->getResult();
    }

    /**
     * @return array<string, mixed>
     */
    private function serializeCommand(GatewayCommand $cmd): array
    {
        return [
            'id' => $cmd->getId(),
            'kind' => $cmd->getKind(),
            'payload' => $cmd->getPayload(),
            'created_at' => $cmd->getCreatedAt()->format(DATE_ATOM),
            'expires_at' => $cmd->getExpiresAt()?->format(DATE_ATOM),
        ];
    }
}
