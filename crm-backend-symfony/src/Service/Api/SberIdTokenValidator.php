<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Service\Admin\SberIdOAuthService;

/**
 * Проверка claims id_token Сбер ID (exp, iss, aud). Подпись GOST3410 в PHP не верифицируется —
 * профиль дополнительно подтверждается через GET userinfo по mTLS.
 */
final class SberIdTokenValidator
{
    private const ALLOWED_ISS = ['id.sber.ru', 'https://id.sber.ru'];

    public function __construct(
        private readonly SberIdOAuthService $sberId,
        private readonly string $clientId,
    ) {
    }

    /**
     * @throws \InvalidArgumentException
     *
     * @return array<string, mixed>
     */
    public function validateAndDecode(string $jwt, ?string $expectedNonce = null): array
    {
        $claims = $this->sberId->decodeIdTokenPayload($jwt);
        if ($claims === []) {
            throw new \InvalidArgumentException('id_token: не удалось разобрать JWT');
        }

        $exp = $claims['exp'] ?? null;
        if (!is_int($exp) && !(is_string($exp) && ctype_digit($exp))) {
            throw new \InvalidArgumentException('id_token: отсутствует exp');
        }
        if ((int) $exp < time()) {
            throw new \InvalidArgumentException('id_token: истёк срок действия (exp)');
        }

        $iss = isset($claims['iss']) && is_string($claims['iss']) ? $claims['iss'] : '';
        if ($iss === '' || !in_array($iss, self::ALLOWED_ISS, true)) {
            throw new \InvalidArgumentException('id_token: недопустимый iss');
        }

        $aud = $claims['aud'] ?? null;
        $audOk = false;
        if (is_string($aud) && trim($this->clientId) !== '' && hash_equals(trim($this->clientId), $aud)) {
            $audOk = true;
        }
        if (is_array($aud) && trim($this->clientId) !== '') {
            foreach ($aud as $a) {
                if (is_string($a) && hash_equals(trim($this->clientId), $a)) {
                    $audOk = true;
                    break;
                }
            }
        }
        if (!$audOk && trim($this->clientId) !== '') {
            throw new \InvalidArgumentException('id_token: aud не совпадает с client_id');
        }

        if ($expectedNonce !== null && $expectedNonce !== '') {
            $nonce = isset($claims['nonce']) && is_string($claims['nonce']) ? $claims['nonce'] : '';
            if ($nonce === '' || !hash_equals($expectedNonce, $nonce)) {
                throw new \InvalidArgumentException('id_token: nonce не совпадает');
            }
        }

        return $claims;
    }
}
