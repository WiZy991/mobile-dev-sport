<?php

namespace App\Entity\Contract;

use App\Entity\Organization;

interface TenantAware
{
    public function getOrganization(): ?Organization;

    public function setOrganization(?Organization $organization): self;
}
