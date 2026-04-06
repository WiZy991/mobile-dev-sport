<?php

namespace App\Service\Integration;

/** Параметры подключения к PERCo-Web (модуль WM04, HTTP/JSON API). */
final readonly class PercoClubConfig
{
    public function __construct(
        public string $baseUrl,
        public string $login,
        public string $password,
        public bool $verifyPeer,
    ) {}
}
