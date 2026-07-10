<?php

declare(strict_types=1);

namespace App\Service\Admin;

use App\Entity\ClubSetting;
use App\Entity\Organization;
use App\Repository\ClubSettingRepository;
use App\Service\Tenant\TenantContext;
use Doctrine\ORM\EntityManagerInterface;

final class ClubSettingsStore
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClubSettingRepository $settings,
        private readonly TenantContext $tenantContext,
    ) {
    }

    public function get(string $key, ?Organization $organization = null): ?string
    {
        $organization ??= $this->tenantContext->getOrganization();
        if ($organization === null) {
            return null;
        }

        return $this->settings->findOneByOrganizationAndKey($organization, $key)?->getSettingValue();
    }

    public function set(string $key, ?string $value, ?Organization $organization = null): void
    {
        $organization ??= $this->tenantContext->requireOrganization();

        $setting = $this->settings->findOneByOrganizationAndKey($organization, $key);
        if ($setting === null) {
            $setting = (new ClubSetting())
                ->setOrganization($organization)
                ->setSettingKey($key);
            $this->em->persist($setting);
        }

        $setting->setSettingValue($value);
    }
}
