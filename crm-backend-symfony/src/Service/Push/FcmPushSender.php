<?php

namespace App\Service\Push;

use App\Entity\PushToken;
use App\Entity\StaffPushToken;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Отправка push через FCM HTTP v1 (OAuth2 + сервисный аккаунт Firebase).
 *
 * Конфигурация (.env / окружение):
 *  - FCM_CREDENTIALS_JSON — путь к JSON сервисного аккаунта Firebase ИЛИ сам JSON строкой.
 *  - FCM_PROJECT_ID — project_id Firebase (необязательно; берётся из JSON, если не задан).
 *
 * Если креды не заданы — отправка тихо пропускается (как и раньше при пустом ключе).
 * Legacy-параметр $serverKey оставлен для обратной совместимости конфигурации, но больше
 * не используется: старый эндпоинт fcm/send отключён Google в 2024 году.
 */
final class FcmPushSender
{
    private const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
    private const TOKEN_URL = 'https://oauth2.googleapis.com/token';

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ?string $serverKey = null,
        private readonly ?string $credentials = null,
        private readonly ?string $projectId = null,
        private readonly ?ApnsPushSender $apnsPushSender = null,
    ) {
    }

    /**
     * @param list<string>          $tokens
     * @param array<string, mixed>  $data
     */
    public function sendToTokens(array $tokens, string $title, string $body, array $data = []): void
    {
        $tokens = array_values(array_unique(array_filter($tokens)));
        if ($tokens === []) {
            return;
        }

        $sa = $this->loadServiceAccount();
        if ($sa === null) {
            return;
        }
        $projectId = $this->projectId ?: ($sa['project_id'] ?? null);
        if (!is_string($projectId) || $projectId === '') {
            return;
        }
        $accessToken = $this->getAccessToken($sa);
        if ($accessToken === null) {
            return;
        }

        // В v1 значения data — строки.
        $stringData = [];
        foreach ($data as $k => $v) {
            $stringData[(string) $k] = is_scalar($v) ? (string) $v : json_encode($v, JSON_UNESCAPED_UNICODE);
        }

        $url = "https://fcm.googleapis.com/v1/projects/{$projectId}/messages:send";
        foreach ($tokens as $token) {
            $message = [
                'message' => [
                    'token' => $token,
                    'notification' => [
                        'title' => $title,
                        'body' => $body,
                    ],
                    'data' => $stringData,
                    'android' => ['priority' => 'high'],
                    'apns' => [
                        'headers' => [
                            'apns-priority' => '10',
                        ],
                        'payload' => [
                            'aps' => [
                                'sound' => 'default',
                            ],
                        ],
                    ],
                ],
            ];
            $this->postJson($url, $message, ['Authorization: Bearer ' . $accessToken]);
        }
    }

    /**
     * @param list<int>             $staffUserIds
     * @param array<string, mixed>  $data
     */
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

    /**
     * @param list<int>             $userIds
     * @param array<string, mixed>  $data
     */
    public function sendToClientUserIds(array $userIds, string $title, string $body, array $data = []): void
    {
        if ($userIds === []) {
            return;
        }
        $rows = $this->em->createQueryBuilder()
            ->select('p.token', 'p.platform')
            ->from(PushToken::class, 'p')
            ->where('p.user IN (:ids)')
            ->setParameter('ids', $userIds)
            ->getQuery()
            ->getArrayResult();

        $fcmTokens = [];
        $apnsTokens = [];
        foreach ($rows as $row) {
            $token = (string) ($row['token'] ?? '');
            if ($token === '') {
                continue;
            }
            $platform = strtolower((string) ($row['platform'] ?? 'android'));
            // Нативный APNs device token — 64 hex-символа; FCM registration token длиннее.
            if ($platform === 'ios' && preg_match('/^[0-9a-fA-F]{64}$/', $token)) {
                $apnsTokens[] = $token;
            } else {
                $fcmTokens[] = $token;
            }
        }

        $this->sendToTokens($fcmTokens, $title, $body, $data);
        $this->apnsPushSender?->sendToTokens($apnsTokens, $title, $body, $data);
    }

    /** @return array<string, mixed>|null */
    private function loadServiceAccount(): ?array
    {
        $raw = $this->credentials;
        if (!is_string($raw) || trim($raw) === '') {
            return null;
        }
        $raw = trim($raw);
        // Путь к файлу или сам JSON.
        if ($raw[0] !== '{' && is_file($raw)) {
            $raw = (string) file_get_contents($raw);
        }
        $decoded = json_decode($raw, true);
        if (!is_array($decoded) || empty($decoded['client_email']) || empty($decoded['private_key'])) {
            return null;
        }

        return $decoded;
    }

    /**
     * OAuth2 access-token по сервисному аккаунту (JWT grant), с файловым кэшем до истечения.
     *
     * @param array<string, mixed> $sa
     */
    private function getAccessToken(array $sa): ?string
    {
        $clientEmail = (string) $sa['client_email'];
        $cacheFile = sys_get_temp_dir() . '/fcm_v1_' . sha1($clientEmail) . '.json';
        $now = time();
        if (is_file($cacheFile)) {
            $cached = json_decode((string) file_get_contents($cacheFile), true);
            if (is_array($cached) && ($cached['exp'] ?? 0) > $now + 60 && !empty($cached['token'])) {
                return (string) $cached['token'];
            }
        }

        $jwt = $this->buildJwt($sa, $now);
        if ($jwt === null) {
            return null;
        }

        $post = http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $jwt,
        ]);
        $context = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => "Content-Type: application/x-www-form-urlencoded\r\n",
                'content' => $post,
                'timeout' => 8,
                'ignore_errors' => true,
            ],
        ]);
        $response = @file_get_contents(self::TOKEN_URL, false, $context);
        if ($response === false) {
            return null;
        }
        $data = json_decode($response, true);
        if (!is_array($data) || empty($data['access_token'])) {
            return null;
        }

        $token = (string) $data['access_token'];
        $expiresIn = (int) ($data['expires_in'] ?? 3600);
        @file_put_contents(
            $cacheFile,
            json_encode(['token' => $token, 'exp' => $now + $expiresIn], JSON_UNESCAPED_SLASHES)
        );

        return $token;
    }

    /** @param array<string, mixed> $sa */
    private function buildJwt(array $sa, int $now): ?string
    {
        $header = $this->base64Url((string) json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
        $claim = $this->base64Url((string) json_encode([
            'iss' => $sa['client_email'],
            'scope' => self::SCOPE,
            'aud' => self::TOKEN_URL,
            'iat' => $now,
            'exp' => $now + 3600,
        ]));
        $signingInput = $header . '.' . $claim;

        $signature = '';
        $ok = openssl_sign($signingInput, $signature, (string) $sa['private_key'], OPENSSL_ALGO_SHA256);
        if (!$ok) {
            return null;
        }

        return $signingInput . '.' . $this->base64Url($signature);
    }

    private function base64Url(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    /**
     * @param array<string, mixed> $payload
     * @param list<string>         $headers
     */
    private function postJson(string $url, array $payload, array $headers = []): void
    {
        $body = json_encode($payload, JSON_UNESCAPED_UNICODE);
        if ($body === false) {
            return;
        }

        $headerLines = array_merge(['Content-Type: application/json'], $headers);
        $context = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => implode("\r\n", $headerLines) . "\r\n",
                'content' => $body,
                'timeout' => 6,
                'ignore_errors' => true,
            ],
        ]);
        @file_get_contents($url, false, $context);
    }
}
