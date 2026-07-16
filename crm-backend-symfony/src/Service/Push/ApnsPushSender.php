<?php

declare(strict_types=1);

namespace App\Service\Push;

/**
 * Отправка push на iOS через APNs HTTP/2 (.p8 ключ).
 *
 * Конфигурация (.env):
 *  - APNS_KEY_ID
 *  - APNS_TEAM_ID
 *  - APNS_BUNDLE_ID (например co.worldcashbox.WorldFintess)
 *  - APNS_AUTH_KEY — путь к AuthKey_XXX.p8 или содержимое PEM
 *  - APNS_PRODUCTION — 1 для api.push.apple.com, 0 для api.sandbox.push.apple.com
 *
 * Если креды не заданы — отправка тихо пропускается.
 */
final class ApnsPushSender
{
    private ?string $cachedJwt = null;
    private int $cachedJwtExp = 0;

    public function __construct(
        private readonly ?string $keyId = null,
        private readonly ?string $teamId = null,
        private readonly ?string $bundleId = null,
        private readonly ?string $authKey = null,
        private readonly bool $production = true,
    ) {
    }

    /**
     * @param list<string>         $tokens hex device tokens
     * @param array<string, mixed> $data
     */
    public function sendToTokens(array $tokens, string $title, string $body, array $data = []): void
    {
        $tokens = array_values(array_unique(array_filter($tokens)));
        if ($tokens === []) {
            return;
        }

        $jwt = $this->getJwt();
        $bundleId = trim((string) $this->bundleId);
        if ($jwt === null || $bundleId === '') {
            return;
        }

        $host = $this->production ? 'https://api.push.apple.com' : 'https://api.sandbox.push.apple.com';
        $payload = json_encode([
            'aps' => [
                'alert' => [
                    'title' => $title,
                    'body' => $body,
                ],
                'sound' => 'default',
            ],
            'type' => (string) ($data['type'] ?? ''),
            'referenceId' => (string) ($data['referenceId'] ?? ''),
        ], JSON_UNESCAPED_UNICODE);
        if ($payload === false) {
            return;
        }

        foreach ($tokens as $token) {
            $token = strtolower(preg_replace('/[^0-9a-f]/i', '', $token) ?? '');
            if ($token === '') {
                continue;
            }
            $this->post($host . '/3/device/' . $token, $payload, $jwt, $bundleId);
        }
    }

    private function getJwt(): ?string
    {
        $now = time();
        if ($this->cachedJwt !== null && $this->cachedJwtExp > $now + 60) {
            return $this->cachedJwt;
        }

        $keyId = trim((string) $this->keyId);
        $teamId = trim((string) $this->teamId);
        $privateKey = $this->loadPrivateKey();
        if ($keyId === '' || $teamId === '' || $privateKey === null) {
            return null;
        }

        $header = $this->b64(json_encode(['alg' => 'ES256', 'kid' => $keyId], JSON_UNESCAPED_SLASHES));
        $claims = $this->b64(json_encode(['iss' => $teamId, 'iat' => $now], JSON_UNESCAPED_SLASHES));
        $unsigned = $header . '.' . $claims;

        $signature = '';
        $ok = openssl_sign($unsigned, $signature, $privateKey, OPENSSL_ALGO_SHA256);
        if (!$ok) {
            return null;
        }

        // APNs ожидает raw R||S (64 байта), а openssl_sign для EC даёт DER.
        $raw = $this->derToJose($signature);
        if ($raw === null) {
            return null;
        }

        $jwt = $unsigned . '.' . $this->b64($raw);
        $this->cachedJwt = $jwt;
        $this->cachedJwtExp = $now + 3500;

        return $jwt;
    }

    private function loadPrivateKey(): ?\OpenSSLAsymmetricKey
    {
        $raw = $this->authKey;
        if (!is_string($raw) || trim($raw) === '') {
            return null;
        }
        $raw = trim($raw);
        if ($raw[0] !== '-' && is_file($raw)) {
            $raw = (string) file_get_contents($raw);
        }
        $key = openssl_pkey_get_private($raw);

        return $key instanceof \OpenSSLAsymmetricKey ? $key : null;
    }

    private function post(string $url, string $payload, string $jwt, string $bundleId): void
    {
        if (!function_exists('curl_init')) {
            return;
        }

        $ch = curl_init($url);
        if ($ch === false) {
            return;
        }

        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_2_0,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 8,
            CURLOPT_HTTPHEADER => [
                'authorization: bearer ' . $jwt,
                'apns-topic: ' . $bundleId,
                'apns-push-type: alert',
                'apns-priority: 10',
                'content-type: application/json',
            ],
            CURLOPT_POSTFIELDS => $payload,
        ]);
        curl_exec($ch);
        curl_close($ch);
    }

    private function b64(string $value): string
    {
        return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
    }

    /** Конвертация ECDSA DER-подписи в raw R||S (ES256). */
    private function derToJose(string $der): ?string
    {
        $offset = 0;
        if (!isset($der[$offset]) || ord($der[$offset]) !== 0x30) {
            return null;
        }
        ++$offset;
        $length = ord($der[$offset]);
        ++$offset;
        if ($length & 0x80) {
            $nbytes = $length & 0x7f;
            $offset += $nbytes;
        }
        if (!isset($der[$offset]) || ord($der[$offset]) !== 0x02) {
            return null;
        }
        ++$offset;
        $rLen = ord($der[$offset]);
        ++$offset;
        $r = substr($der, $offset, $rLen);
        $offset += $rLen;
        if (!isset($der[$offset]) || ord($der[$offset]) !== 0x02) {
            return null;
        }
        ++$offset;
        $sLen = ord($der[$offset]);
        ++$offset;
        $s = substr($der, $offset, $sLen);

        $r = ltrim($r, "\x00");
        $s = ltrim($s, "\x00");
        $r = str_pad($r, 32, "\x00", STR_PAD_LEFT);
        $s = str_pad($s, 32, "\x00", STR_PAD_LEFT);
        if (strlen($r) !== 32 || strlen($s) !== 32) {
            return null;
        }

        return $r . $s;
    }
}
