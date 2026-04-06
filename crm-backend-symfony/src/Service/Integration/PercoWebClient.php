<?php

namespace App\Service\Integration;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Клиент PERCo-Web API (JSON). Типовые методы из официальных примеров.
 */
final class PercoWebClient
{
    public function __construct(
        private readonly HttpClientInterface $http,
        private readonly PercoClubConfigFactory $configFactory,
    ) {}

    public function isConfigured(): bool
    {
        return $this->configFactory->load() instanceof PercoClubConfig;
    }

    /** Проверка логина к PERCo-Web (POST /api/system/auth). */
    public function testAuthentication(): void
    {
        $this->authenticate($this->requireConfig());
    }

    /**
     * Выдать основную карту/идентификатор пользователю в PERCo (POST /api/users/{id}/mainCard).
     *
     * @param int    $percoUserId Внутренний ID пользователя в PERCo-Web
     * @param string $identifier Номер карты / код для считывателя (строка по документации PERCo)
     */
    public function assignMainCard(int $percoUserId, string $identifier): void
    {
        $config = $this->requireConfig();
        $token = $this->authenticate($config);
        $url = $this->endpoint($config, '/api/users/' . $percoUserId . '/mainCard');
        $response = $this->http->request('POST', $url, [
            'headers' => [
                'Authorization' => 'Bearer ' . $token,
                'Accept' => 'application/json',
            ],
            'json' => ['identifier' => $identifier],
            'verify_peer' => $config->verifyPeer,
            'timeout' => 20,
        ]);

        if ($response->getStatusCode() !== 200) {
            throw new \RuntimeException('PERCo mainCard: HTTP ' . $response->getStatusCode() . ' ' . $response->getContent(false));
        }
    }

    private function requireConfig(): PercoClubConfig
    {
        $c = $this->configFactory->load();
        if (!$c) {
            throw new \RuntimeException('Интеграция PERCo-Web не настроена или выключена.');
        }

        return $c;
    }

    private function endpoint(PercoClubConfig $config, string $path): string
    {
        return rtrim($config->baseUrl, '/') . $path;
    }

    private function authenticate(PercoClubConfig $config): string
    {
        $url = $this->endpoint($config, '/api/system/auth');
        $response = $this->http->request('POST', $url, [
            'headers' => ['Accept' => 'application/json'],
            'json' => [
                'login' => $config->login,
                'password' => $config->password,
            ],
            'verify_peer' => $config->verifyPeer,
            'timeout' => 15,
        ]);

        $data = json_decode($response->getContent(false), true);
        if (!is_array($data)) {
            throw new \RuntimeException('PERCo auth: неверный ответ сервера.');
        }

        if ($response->getStatusCode() !== 200) {
            $err = $data['error'] ?? $response->getContent(false);
            throw new \RuntimeException('PERCo auth: ' . (is_string($err) ? $err : json_encode($err)));
        }

        $token = $data['token'] ?? null;
        if (!is_string($token) || $token === '') {
            throw new \RuntimeException('PERCo auth: в ответе нет token.');
        }

        return $token;
    }
}
