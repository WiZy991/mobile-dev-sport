<?php

namespace App\Service\Integration;

use Psr\Log\LoggerInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Клиент PERCo-Web API (JSON). Типовые методы из официальных примеров.
 */
final class PercoWebClient
{
    public function __construct(
        private readonly HttpClientInterface $http,
        private readonly PercoClubConfigFactory $configFactory,
        private readonly LoggerInterface $logger,
    ) {}

    public function isConfigured(): bool
    {
        return $this->configFactory->load() instanceof PercoClubConfig;
    }

    /** Настроено ли устройство для открытия при успешной проверке QR (см. «Настройки клуба»). */
    public function canOpenEntryFromApi(): bool
    {
        $c = $this->configFactory->load();

        return $c instanceof PercoClubConfig && $c->hasEntryDevice();
    }

    /**
     * После успешной проверки QR — отправить команду на исполнительное устройство (открытие турникета).
     * Ошибки логируются; не прерывают бизнес-логику доступа.
     *
     * @return bool|null null — интеграция выкл. или device id не задан; true — команда ушла; false — сбой HTTP/PERCo
     */
    public function tryOpenEntryAfterGranted(): ?bool
    {
        $config = $this->configFactory->load();
        if (!$config instanceof PercoClubConfig || !$config->hasEntryDevice()) {
            return null;
        }

        try {
            $this->sendDeviceCommand(
                $config,
                $config->entryDeviceId,
                $config->openCmdNumber,
                $config->openCmdType,
                $config->openCmdValue,
                $config->openCmdParam,
            );

            return true;
        } catch (\Throwable $e) {
            $this->logger->error('PERCo: не удалось отправить команду открытия', [
                'device_id' => $config->entryDeviceId,
                'message' => $e->getMessage(),
            ]);

            return false;
        }
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

    /**
     * Отправка команды на устройство (турникет, замок и т.д.).
     *
     * @see https://github.com/percodev/api_examples/blob/main/devices/nodejs/devicesIdCommandPOST.node.ts
     */
    public function sendDeviceCommand(
        PercoClubConfig $config,
        int $deviceId,
        int $cmdNumber,
        int $cmdType,
        int $cmdValue,
        int $cmdParam,
    ): void {
        $token = $this->authenticate($config);
        $url = $this->endpoint($config, '/api/devices/' . $deviceId . '/command');
        $response = $this->http->request('POST', $url, [
            'headers' => [
                'Authorization' => 'Bearer ' . $token,
                'Accept' => 'application/json',
            ],
            'json' => [
                'cmdNumber' => $cmdNumber,
                'cmdType' => $cmdType,
                'cmdValue' => $cmdValue,
                'cmdParam' => $cmdParam,
            ],
            'verify_peer' => $config->verifyPeer,
            'timeout' => 20,
        ]);

        if ($response->getStatusCode() !== 200) {
            throw new \RuntimeException('PERCo command: HTTP ' . $response->getStatusCode() . ' ' . $response->getContent(false));
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
