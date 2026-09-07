<?php

declare(strict_types=1);

namespace App\Service\App;

use App\Service\Admin\ClubSettingsStore;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Запоминает максимальный versionCode, который реально открывали в проде.
 * Старые сборки сравнивают себя с этим числом — CRM трогать не нужно.
 */
final class AndroidVersionBeacon
{
    private const KNOWN_APPLICATION_IDS = [
        'ru.worldcashfit.app',
        'ru.academywrestling.app',
    ];

    private const MAX_CODE = 500;
    private const MAX_JUMP = 40;

    public function __construct(
        private readonly ClubSettingsStore $settings,
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function remember(string $applicationId, int $versionCode): void
    {
        $applicationId = trim($applicationId);
        if (!\in_array($applicationId, self::KNOWN_APPLICATION_IDS, true)) {
            return;
        }
        if ($versionCode < 1 || $versionCode > self::MAX_CODE) {
            return;
        }

        $key = $this->seenKey($applicationId);
        $seen = max(0, (int) ($this->settings->get($key) ?? 0));
        if ($versionCode <= $seen) {
            return;
        }
        if ($seen > 0 && ($versionCode - $seen) > self::MAX_JUMP) {
            return;
        }

        $this->settings->set($key, (string) $versionCode);
        $this->em->flush();
    }

    public function effectiveMinVersionCode(string $applicationId): int
    {
        $manual = max(0, (int) ($this->settings->get('android_min_version_code') ?? 0));
        $applicationId = trim($applicationId);
        $seen = 0;
        if (\in_array($applicationId, self::KNOWN_APPLICATION_IDS, true)) {
            $seen = max(0, (int) ($this->settings->get($this->seenKey($applicationId)) ?? 0));
        } else {
            foreach (self::KNOWN_APPLICATION_IDS as $id) {
                $seen = max($seen, (int) ($this->settings->get($this->seenKey($id)) ?? 0));
            }
        }

        return max($manual, $seen);
    }

    private function seenKey(string $applicationId): string
    {
        return 'android_seen_version_' . str_replace('.', '_', $applicationId);
    }
}
