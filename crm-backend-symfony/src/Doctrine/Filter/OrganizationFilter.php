<?php

namespace App\Doctrine\Filter;

use App\Entity\Contract\TenantAware;
use Doctrine\ORM\Mapping\ClassMetadata;
use Doctrine\ORM\Query\Filter\SQLFilter;

final class OrganizationFilter extends SQLFilter
{
    public function addFilterConstraint(ClassMetadata $targetEntity, $targetTable): string
    {
        if (!is_a($targetEntity->getName(), TenantAware::class, true)) {
            return '';
        }

        $orgId = $this->getParameter('organization_id');
        if ($orgId === '' || $orgId === '0') {
            return '';
        }

        return sprintf('%s.organization_id = %s', $targetTable, $orgId);
    }
}
