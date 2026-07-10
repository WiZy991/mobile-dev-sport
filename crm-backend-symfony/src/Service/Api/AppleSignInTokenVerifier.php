<?php

declare(strict_types=1);

namespace App\Service\Api;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Проверка identity token от Sign in with Apple (JWT RS256, JWKS Apple).
 */
final class AppleSignInTokenVerifier
{
    private const JWKS_URL = 'https://appleid.apple.com/auth/keys';
    private const ISSUER = 'https://appleid.apple.com';

    /** @var array<string, string>|null kid => PEM */
    private static ?array $pemCache = null;

    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $clientId,
    ) {
    }

    /**
     * @return array{sub: string, email?: string, email_verified?: bool|string}
     */
    public function verify(string $identityToken): array
    {
        $identityToken = trim($identityToken);
        if ($identityToken === '') {
            throw new \InvalidArgumentException('Пустой identity token');
        }

        $parts = explode('.', $identityToken);
        if (\count($parts) !== 3) {
            throw new \InvalidArgumentException('Некорректный формат identity token');
        }

        [$headerB64, $payloadB64, $signatureB64] = $parts;
        $header = json_decode($this->base64UrlDecode($headerB64), true, 512, JSON_THROW_ON_ERROR);
        $payload = json_decode($this->base64UrlDecode($payloadB64), true, 512, JSON_THROW_ON_ERROR);
        $signature = $this->base64UrlDecode($signatureB64);

        $kid = (string) ($header['kid'] ?? '');
        $alg = (string) ($header['alg'] ?? '');
        if ($kid === '' || $alg !== 'RS256') {
            throw new \InvalidArgumentException('Неподдерживаемый заголовок Apple token');
        }

        $pem = $this->pemForKeyId($kid);
        $signed = $headerB64 . '.' . $payloadB64;
        $ok = openssl_verify($signed, $signature, $pem, OPENSSL_ALGO_SHA256);
        if ($ok !== 1) {
            throw new \InvalidArgumentException('Подпись Apple token не прошла проверку');
        }

        $now = time();
        $exp = (int) ($payload['exp'] ?? 0);
        if ($exp > 0 && $exp < $now - 60) {
            throw new \InvalidArgumentException('Срок действия Apple token истёк');
        }

        if (($payload['iss'] ?? '') !== self::ISSUER) {
            throw new \InvalidArgumentException('Некорректный iss в Apple token');
        }

        $aud = $payload['aud'] ?? null;
        if (\is_array($aud)) {
            if (!\in_array($this->clientId, $aud, true)) {
                throw new \InvalidArgumentException('Некорректный aud в Apple token');
            }
        } elseif ((string) $aud !== $this->clientId) {
            throw new \InvalidArgumentException('Некорректный aud в Apple token');
        }

        $sub = trim((string) ($payload['sub'] ?? ''));
        if ($sub === '') {
            throw new \InvalidArgumentException('В Apple token отсутствует sub');
        }

        $result = ['sub' => $sub];
        if (isset($payload['email']) && \is_string($payload['email']) && $payload['email'] !== '') {
            $result['email'] = mb_strtolower(trim($payload['email']));
        }
        if (isset($payload['email_verified'])) {
            $result['email_verified'] = $payload['email_verified'];
        }

        return $result;
    }

    private function pemForKeyId(string $kid): string
    {
        if (self::$pemCache === null) {
            self::$pemCache = [];
        }
        if (isset(self::$pemCache[$kid])) {
            return self::$pemCache[$kid];
        }

        $response = $this->httpClient->request('GET', self::JWKS_URL);
        $data = $response->toArray(false);
        foreach ($data['keys'] ?? [] as $jwk) {
            if (!\is_array($jwk) || ($jwk['kid'] ?? '') !== $kid) {
                continue;
            }
            $pem = $this->jwkToPem($jwk);
            self::$pemCache[$kid] = $pem;

            return $pem;
        }

        throw new \InvalidArgumentException('Ключ Apple JWKS не найден');
    }

    /** @param array<string, mixed> $jwk */
    private function jwkToPem(array $jwk): string
    {
        $n = $this->base64UrlDecode((string) ($jwk['n'] ?? ''));
        $e = $this->base64UrlDecode((string) ($jwk['e'] ?? ''));

        $modulus = $this->encodeAsn1Integer($n);
        $exponent = $this->encodeAsn1Integer($e);
        $rsaPublicKey = $this->encodeAsn1Sequence($modulus . $exponent);
        $bitString = "\x03" . $this->encodeLength(\strlen($rsaPublicKey) + 1) . "\x00" . $rsaPublicKey;
        $algorithmIdentifier = hex2bin('300D06092A864886F70D0101010500');
        $publicKeyInfo = $this->encodeAsn1Sequence($algorithmIdentifier . $bitString);
        $pem = "-----BEGIN PUBLIC KEY-----\n"
            . chunk_split(base64_encode($publicKeyInfo), 64, "\n")
            . "-----END PUBLIC KEY-----\n";

        return $pem;
    }

    private function encodeAsn1Integer(string $bytes): string
    {
        if ($bytes === '') {
            $bytes = "\x00";
        }
        if (ord($bytes[0]) > 127) {
            $bytes = "\x00" . $bytes;
        }

        return "\x02" . $this->encodeLength(\strlen($bytes)) . $bytes;
    }

    private function encodeAsn1Sequence(string $contents): string
    {
        return "\x30" . $this->encodeLength(\strlen($contents)) . $contents;
    }

    private function encodeLength(int $length): string
    {
        if ($length < 0x80) {
            return \chr($length);
        }
        $temp = ltrim(pack('N', $length), "\x00");
        if ($temp === '') {
            $temp = "\x00";
        }

        return \chr(0x80 | \strlen($temp)) . $temp;
    }

    private function base64UrlDecode(string $data): string
    {
        $remainder = \strlen($data) % 4;
        if ($remainder > 0) {
            $data .= str_repeat('=', 4 - $remainder);
        }
        $decoded = base64_decode(strtr($data, '-_', '+/'), true);
        if ($decoded === false) {
            throw new \InvalidArgumentException('Ошибка base64url');
        }

        return $decoded;
    }
}
