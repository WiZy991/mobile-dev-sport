<?php

namespace App\Service\Integration;

/**
 * Параметры подключения к PERCo-Web (модуль WM04, HTTP/JSON API).
 *
 * @see https://github.com/percodev/api_examples/blob/main/devices/nodejs/devicesIdCommandPOST.node.ts
 */
final readonly class PercoClubConfig
{
    public function __construct(
        public string $baseUrl,
        public string $login,
        public string $password,
        public bool $verifyPeer,
        /** ID исполнительного устройства (турникет) в PERCo-Web — см. раздел «Устройства». */
        public ?int $entryDeviceId,
        /** Команда открытия: значения по умолчанию из официального примера. */
        public int $openCmdNumber = 1,
        public int $openCmdType = 0,
        public int $openCmdValue = 3,
        public int $openCmdParam = 5000,
    ) {}

    public function hasEntryDevice(): bool
    {
        return $this->entryDeviceId !== null && $this->entryDeviceId > 0;
    }
}
