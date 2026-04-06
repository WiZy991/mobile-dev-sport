<?php

namespace App\Service\Api;

/**
 * Подписанный state для OAuth мобильного приложения (без серверной сессии).
 */
final class SberMobileOAuthStateService
{
    private readonly string $secret;

    public function __construct(string $secret)
    {
        $this->secret = $secret !== '' ? $secret : 'dev-change-me';
    }

    public function createState(int $userId, int $ttlSeconds = 900): string
    {
        $exp = time() + $ttlSeconds;
        $payload = json_encode(['uid' => $userId, 'exp' => $exp], JSON_THROW_ON_ERROR);
        $b64 = $this->base64UrlEncode($payload);
        $sig = $this->sign($b64);

        return $b64 . '.' . $sig;
    }

    /** @return int|null user id */
    public function verifyAndGetUserId(string $state): ?int
    {
        $parts = explode('.', $state, 2);
        if (count($parts) !== 2) {
            return null;
        }
        [$b64, $sig] = $parts;
        if (!hash_equals($this->sign($b64), $sig)) {
            return null;
        }
        try {
            $json = $this->base64UrlDecode($b64);
            $data = json_decode($json, true, 512, JSON_THROW_ON_ERROR);
        } catch (\Throwable) {
            return null;
        }
        if (!is_array($data) || !isset($data['uid'], $data['exp'])) {
            return null;
        }
        if (!is_int($data['uid']) && !ctype_digit((string) $data['uid'])) {
            return null;
        }
        if (time() > (int) $data['exp']) {
            return null;
        }

        return (int) $data['uid'];
    }

    private function sign(string $data): string
    {
        return $this->base64UrlEncode(hash_hmac('sha256', $data, $this->secret, true));
    }

    private function base64UrlEncode(string $raw): string
    {
        return rtrim(strtr(base64_encode($raw), '+/', '-_'), '=');
    }

    private function base64UrlDecode(string $b64): string
    {
        $pad = 4 - (strlen($b64) % 4);
        if ($pad !== 4) {
            $b64 .= str_repeat('=', $pad);
        }
        $raw = base64_decode(strtr($b64, '-_', '+/'), true);

        return $raw === false ? '' : $raw;
    }
}
