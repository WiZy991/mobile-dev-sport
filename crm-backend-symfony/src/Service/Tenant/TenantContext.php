<?php

declare(strict_types=1);

namespace App\Service\Tenant;

use App\Entity\Organization;
use App\Entity\StaffUser;
use Symfony\Bundle\SecurityBundle\Security;

final class TenantContext
{
    private ?Organization $organization = null;

    public function __construct(
        private readonly Security $security,
        private readonly OrganizationResolver $organizationResolver,
    ) {
    }

    public function setOrganization(?Organization $organization): void
    {
        $this->organization = $organization;
    }

    public function getOrganization(): ?Organization
    {
        if ($this->organization !== null) {
            return $this->organization;
        }

        return $this->organizationResolver->resolve();
    }

    public function getOrganizationId(): ?int
    {
        return $this->getOrganization()?->getId();
    }

    public function isPlatformAdmin(): bool
    {
        $user = $this->security->getUser();

        return $user instanceof StaffUser
            && \in_array('ROLE_PLATFORM_ADMIN', $user->getRoles(), true);
    }

    public function shouldApplyTenantFilter(): bool
    {
        if ($this->isPlatformAdmin()) {
            return false;
        }

        return $this->getOrganization() !== null;
    }

    public function requireOrganization(): Organization
    {
        $org = $this->getOrganization();
        if ($org === null) {
            throw new \RuntimeException('Организация не определена в текущем контексте.');
        }

        return $org;
    }
}
