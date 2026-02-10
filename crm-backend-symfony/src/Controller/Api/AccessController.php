<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
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
        if (count($parts) !== 4 || $parts[0] !== 'FITNESSCLUB' || $parts[1] !== 'ENTRY') {
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

        // Проверяем наличие активного абонемента
        $today = new \DateTimeImmutable('today');
        $subsRepo = $this->em->getRepository(Subscription::class);
        /** @var Subscription[] $subs */
        $subs = $subsRepo->findBy(['user' => $user, 'status' => 'active']);

        $hasActive = false;
        foreach ($subs as $sub) {
            $start = $sub->getStartDate();
            $end = $sub->getEndDate();

            if ($today < $start) {
                continue;
            }
            if ($end !== null && $today > $end) {
                continue;
            }

            $hasActive = true;
            break;
        }

        if (!$hasActive) {
            $log->setReason('no_active_subscription');
            $response['reason'] = 'no_active_subscription';
            $this->em->persist($log);
            $this->em->flush();

            return $this->json($response, 403);
        }

        // Доступ разрешён
        $log->setResult('granted')
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
}

