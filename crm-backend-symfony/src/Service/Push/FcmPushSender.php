<?php

namespace App\Service\Push;

use App\Entity\StaffPushToken;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Отправка push через FCM Legacy API (если задан FCM_SERVER_KEY в .env).
 */
final class FcmPushSender
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ?string $serverKey = null,
    ) {
    }

    /** @param list<string> $tokens */
    public function sendToTokens(array $tokens, string $title, string $body, array $data = []): void
    {
        if ($this->serverKey === null || $this->serverKey === '' || $tokens === []) {
            return;
        }

        foreach (array_unique($tokens) as $token) {
            $payload = [
                'to' => $token,
                'notification' => [
                    'title' => $title,
                    'body' => $body,
                ],
                'data' => $data,
                'priority' => 'high',
            ];
            $this->postJson('https://fcm.googleapis.com/fcm/send', $payload);
        }
    }

    public function sendToStaffUserIds(array $staffUserIds, string $title, string $body, array $data = []): void
    {
        if ($staffUserIds === []) {
            return;
        }
        $rows = $this->em->createQueryBuilder()
            ->select('p.token')
            ->from(StaffPushToken::class, 'p')
            ->where('p.staffUser IN (:ids)')
            ->setParameter('ids', $staffUserIds)
            ->getQuery()
            ->getScalarResult();

        $tokens = array_map(static fn (array $row) => (string) $row['token'], $rows);
        $this->sendToTokens($tokens, $title, $body, $data);
    }

    /** @param array<string, mixed> $payload */
    private function postJson(string $url, array $payload): void
    {
        $body = json_encode($payload, JSON_UNESCAPED_UNICODE);
        if ($body === false) {
            return;
        }

        $context = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => "Content-Type: application/json\r\nAuthorization: key={$this->serverKey}\r\n",
                'content' => $body,
                'timeout' => 5,
                'ignore_errors' => true,
            ],
        ]);
        @file_get_contents($url, false, $context);
    }
}
