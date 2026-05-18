<?php

namespace App\Service\Api;

/**
 * Подписанный OAuth state для PKCE-потока мобильного приложения (nonce для проверки id_token).
 */
final class SberOAuthPkceStateService
{
    private readonly string $secret;

    public function __construct(string $secret)
    {
        $this->secret = $secret !== '' ? $secret : 'dev-change-me';
    }

    /** @return array{state: string, nonce: string} */
    public function issue(int $ttlSeconds = 900): array
    {
        $nonce = bin2hex(random_bytes(16));
        $exp = time() + $ttlSeconds;
        $payload = json_encode(['nonce' => $nonce, 'exp' => $exp], JSON_THROW_ON_ERROR);
        $b64 = $this->base64UrlEncode($payload);
        $sig = $this->sign($b64);

        return ['state' => $b64 . '.' . $sig, 'nonce' => $nonce];
    }

    /** @return array{nonce: string}|null */
    public function verify(string $state): ?array
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
        if (!is_array($data) || !isset($data['nonce'], $data['exp'])) {
            return null;
        }
        if (!is_string($data['nonce']) || $data['nonce'] === '') {
            return null;
        }
        if (time() > (int) $data['exp']) {
            return null;
        }

        return ['nonce' => $data['nonce']];
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
