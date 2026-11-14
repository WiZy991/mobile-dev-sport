<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Проверка токена Яндекс SmartCaptcha — как в примере из ЛК (PHP).
 * UI виджета — только JS + client key; этот класс лишь validate.
 */
final class YandexSmartCaptchaVerifier
{
    /** URL из примера Яндекса в ЛК. */
    private const VALIDATE_URL = 'https://smartcaptcha.yandexcloud.net/validate';

    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $serverKey = '',
        private readonly bool $enabled = false,
    ) {
    }

    public function isEnabled(): bool
    {
        return $this->enabled && $this->serverKey !== '';
    }

    public function verify(string $token, ?string $ip = null): bool
    {
        if (!$this->isEnabled()) {
            return true;
        }

        $token = trim($token);
        if ($token === '') {
            return false;
        }

        try {
            $query = [
                'secret' => $this->serverKey,
                'token' => $token,
            ];
            if ($ip !== null && $ip !== '' && $ip !== '0.0.0.0') {
                $query['ip'] = $ip;
            }

            // Как в PHP-примере ЛК: GET ?secret&token&ip
            $response = $this->httpClient->request('GET', self::VALIDATE_URL, [
                'query' => $query,
                'timeout' => 3,
            ]);

            // В примере ЛК: при HTTP ≠ 200 — пропускаем (не блокируем из‑за сбоя сервиса).
            if ($response->getStatusCode() !== 200) {
                return true;
            }

            $data = $response->toArray(false);

            return ($data['status'] ?? '') === 'ok';
        } catch (\Throwable) {
            return true;
        }
    }
}
