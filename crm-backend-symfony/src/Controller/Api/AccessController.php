<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\GuestPass;
use App\Entity\Subscription;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/access')]
class AccessController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/entry', name: 'api_access_entry', methods: ['POST'])]
    public function entry(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $qr = (string) ($data['qr'] ?? '');
        $deviceId = $data['device_id'] ?? null;

        $log = new AccessLog();
        $log->setRawData($qr)
            ->setDeviceId($deviceId)
            ->setResult('denied');

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

        // Обычный вход: FITNESSCLUB:ENTRY:user-123:timestamp
        if ($parts[1] !== 'ENTRY' || count($parts) !== 4) {
            $log->setReason('invalid_format');
            $response['reason'] = 'invalid_format';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 400);
        }

        $userExternalId = $parts[2]; // например, user-123
        $timestamp = (int) $parts[3];

        // Проверка времени (5 минут)
        $nowMs = (int) (microtime(true) * 1000);
        if (abs($nowMs - $timestamp) > 5 * 60 * 1000) {
            $log->setReason('qr_expired');
            $response['reason'] = 'qr_expired';
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

        // Проверяем наличие активного абонемента
        $today = new \DateTimeImmutable('today');
        $subsRepo = $this->em->getRepository(Subscription::class);
        /** @var Subscription[] $subs */
        $subs = $subsRepo->findBy(['user' => $user, 'status' => 'active']);

        $activeSub = null;
        foreach ($subs as $sub) {
            $start = $sub->getStartDate();
            $end = $sub->getEndDate();

            if ($today < $start) {
                continue;
            }
            if ($end !== null && $today > $end) {
                continue;
            }

            $activeSub = $sub;
            break;
        }

        if (!$activeSub) {
            $log->setReason('no_active_subscription');
            $response['reason'] = 'no_active_subscription';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        // Доступ разрешён — увеличиваем visits_used у активного абонемента
        if ($activeSub->getVisitsTotal() !== null) {
            $used = (int) ($activeSub->getVisitsUsed() ?? 0);
            $activeSub->setVisitsUsed($used + 1);
            $this->em->persist($activeSub);
        }

        $log->setEventType('entry')
            ->setResult('granted')
            ->setReason('ok');

        $this->em->persist($log);
        $this->em->flush();

        return $this->json([
            'access_granted' => true,
            'reason' => 'ok',
            'user' => [
                'id' => 'user-' . $user->getId(),
                'name' => $user->getName(),
                'phone' => $user->getPhone(),
            ],
        ]);
    }

    #[Route('/exit', name: 'api_access_exit', methods: ['POST'])]
    public function exit(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $qr = (string) ($data['qr'] ?? '');
        $deviceId = $data['device_id'] ?? null;

        $log = new AccessLog();
        $log->setRawData($qr)
            ->setDeviceId($deviceId)
            ->setEventType('exit')
            ->setResult('granted')
            ->setReason('ok');

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

        return $this->json(['success' => true]);
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

        return $this->json([
            'access_granted' => true,
            'reason' => 'ok',
            'user' => [
                'id' => 'guest-' . $guestPass->getId(),
                'name' => $guestPass->getGuestName() ?: ('Гость ' . $owner->getName()),
                'phone' => $owner->getPhone(),
            ],
        ]);
    }
}

