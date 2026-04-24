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

        $deviceRaw = trim((string) $this->get('perco_entry_device_id'));
        $entryDeviceId = $deviceRaw !== '' && ctype_digit($deviceRaw) ? (int) $deviceRaw : null;

        $openCmdNumber = $this->intSetting('perco_cmd_number', 1);
        $openCmdType = $this->intSetting('perco_cmd_type', 0);
        $openCmdValue = $this->intSetting('perco_cmd_value', 3);
        $openCmdParam = $this->intSetting('perco_cmd_param', 5000);

        return new PercoClubConfig(
            $baseUrl,
            $login,
            $password,
            $verifyPeer,
            $entryDeviceId,
            $openCmdNumber,
            $openCmdType,
            $openCmdValue,
            $openCmdParam,
        );
    }

    private function intSetting(string $key, int $default): int
    {
        $raw = trim((string) ($this->get($key) ?? ''));
        if ($raw === '' || !is_numeric($raw)) {
            return $default;
        }

        return (int) $raw;
    }

    private function get(string $key): ?string
    {
        $s = $this->em->getRepository(ClubSetting::class)->find($key);

        return $s?->getSettingValue();
    }
}
