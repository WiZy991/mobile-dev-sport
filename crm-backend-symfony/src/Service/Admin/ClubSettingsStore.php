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
    /** @var array<int, array<string, ?string>> request-scoped: orgId => key => value */
    private array $cacheByOrgId = [];

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

        $map = $this->allForOrganization($organization);

        return \array_key_exists($key, $map) ? $map[$key] : null;
    }

    /**
     * @param list<string> $keys
     * @return array<string, ?string>
     */
    public function getMany(array $keys, ?Organization $organization = null): array
    {
        $organization ??= $this->tenantContext->getOrganization();
        if ($organization === null) {
            return array_fill_keys($keys, null);
        }

        $map = $this->allForOrganization($organization);
        $out = [];
        foreach ($keys as $key) {
            $out[$key] = \array_key_exists($key, $map) ? $map[$key] : null;
        }

        return $out;
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

        $orgId = $organization->getId();
        if ($orgId !== null) {
            $this->cacheByOrgId[$orgId][$key] = $value;
        }
    }

    /**
     * @return array<string, ?string>
     */
    private function allForOrganization(Organization $organization): array
    {
        $orgId = $organization->getId();
        if ($orgId === null) {
            return [];
        }

        if (isset($this->cacheByOrgId[$orgId])) {
            return $this->cacheByOrgId[$orgId];
        }

        /** @var list<ClubSetting> $rows */
        $rows = $this->settings->findBy(['organization' => $organization]);
        $map = [];
        foreach ($rows as $row) {
            $map[$row->getSettingKey()] = $row->getSettingValue();
        }

        return $this->cacheByOrgId[$orgId] = $map;
    }
}
