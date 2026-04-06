<?php

namespace App\Service\Integration;

use App\Entity\ClubSetting;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Читает настройки СКУД из club_settings (экран «Настройки клуба»).
 *
 * @see https://www.perco.ru/products/perco-wm-04-integratsiya-s-vneshnimi-sistemami.php
 * @see https://github.com/percodev/api_examples
 */
final class PercoClubConfigFactory
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    public function load(): ?PercoClubConfig
    {
        $enabled = $this->get('perco_enabled');
        if ($enabled !== '1') {
            return null;
        }

        $baseUrl = trim((string) $this->get('perco_base_url'));
        $login = trim((string) $this->get('perco_login'));
        $password = (string) $this->get('perco_password');

        if ($baseUrl === '' || $login === '' || $password === '') {
            return null;
        }

        $verify = $this->get('perco_verify_ssl');
        $verifyPeer = $verify === '' || $verify === '1';

        return new PercoClubConfig($baseUrl, $login, $password, $verifyPeer);
    }

    private function get(string $key): ?string
    {
        $s = $this->em->getRepository(ClubSetting::class)->find($key);

        return $s?->getSettingValue();
    }
}
