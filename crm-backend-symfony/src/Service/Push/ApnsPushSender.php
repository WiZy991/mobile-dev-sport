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
            error_log('APNs: нет iOS-токенов для отправки (пропуск)');

            return;
        }

        $jwt = $this->getJwt();
        $bundleId = trim((string) $this->bundleId);
        if ($jwt === null || $bundleId === '') {
            error_log(sprintf(
                'APNs: отправка пропущена — не настроены креды (jwt=%s, keyId=%s, teamId=%s, authKey=%s, bundleId=%s). Задайте APNS_* в .env и перезапустите бэкенд.',
                $jwt === null ? 'null' : 'ok',
                trim((string) $this->keyId) === '' ? 'empty' : 'set',
                trim((string) $this->teamId) === '' ? 'empty' : 'set',
                trim((string) $this->authKey) === '' ? 'empty' : 'set',
                $bundleId === '' ? 'empty' : $bundleId,
            ));

            return;
        }

        error_log(sprintf('APNs: отправка %d токен(ов), окружение=%s', count($tokens), $this->production ? 'production' : 'sandbox'));

        $prodHost = 'https://api.push.apple.com';
        $sandboxHost = 'https://api.sandbox.push.apple.com';
        // Сначала пробуем окружение из конфига, затем — второе (сборка из Xcode = sandbox,
        // TestFlight/App Store = production). Так пуш доходит при любой сборке.
        $primaryHost = $this->production ? $prodHost : $sandboxHost;
        $fallbackHost = $this->production ? $sandboxHost : $prodHost;

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

            $suffix = substr($token, -6);

            [$code, $response] = $this->post($primaryHost . '/3/device/' . $token, $payload, $jwt, $bundleId);
            if ($code === 200) {
                error_log(sprintf('APNs: OK HTTP 200 (host=%s, token=...%s)', $primaryHost, $suffix));
                continue;
            }

            // BadDeviceToken/BadEnvironment/Unregistered — токен зарегистрирован в другом
            // APNs-окружении: повторяем на альтернативном хосте.
            if ($this->shouldRetryOnOtherEnvironment($code, $response)) {
                [$retryCode, $retryResponse] = $this->post($fallbackHost . '/3/device/' . $token, $payload, $jwt, $bundleId);
                if ($retryCode === 200) {
                    error_log(sprintf('APNs: OK HTTP 200 после ретрая (host=%s, token=...%s)', $fallbackHost, $suffix));
                } else {
                    $this->logFailure($retryCode, $bundleId, $fallbackHost, $retryResponse);
                }
                continue;
            }

            $this->logFailure($code, $bundleId, $primaryHost, $response);
        }
    }

    private function shouldRetryOnOtherEnvironment(int $code, string $response): bool
    {
        if ($code !== 400 && $code !== 410) {
            return false;
        }

        return str_contains($response, 'BadDeviceToken')
            || str_contains($response, 'BadEnvironmentKeyInToken')
            || str_contains($response, 'DeviceTokenNotForTopic')
            || str_contains($response, 'Unregistered');
    }

    private function logFailure(int $httpCode, string $bundleId, string $host, string $response): void
    {
        error_log(sprintf(
            'APNs push failed HTTP %d (host=%s, topic=%s): %s',
            $httpCode,
            $host,
            $bundleId,
            $response,
        ));
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

    /**
     * @return array{0: int, 1: string} [httpCode, responseBody]
     */
    private function post(string $url, string $payload, string $jwt, string $bundleId): array
    {
        if (!function_exists('curl_init')) {
            return [0, ''];
        }

        $ch = curl_init($url);
        if ($ch === false) {
            return [0, ''];
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
        $response = curl_exec($ch);
        $httpCode = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        return [$httpCode, is_string($response) ? $response : ''];
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
