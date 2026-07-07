<?php

declare(strict_types=1);

namespace App\EventSubscriber\Doctrine;

use App\Entity\Contract\TenantAware;
use App\Service\Tenant\TenantContext;
use Doctrine\Bundle\DoctrineBundle\Attribute\AsDoctrineListener;
use Doctrine\ORM\Event\PrePersistEventArgs;
use Doctrine\ORM\Events;

#[AsDoctrineListener(event: Events::prePersist)]
final class OrganizationAssignSubscriber
{
    public function __construct(
        private readonly TenantContext $tenantContext,
    ) {
    }

    public function prePersist(PrePersistEventArgs $args): void
    {
        $entity = $args->getObject();
        if (!$entity instanceof TenantAware) {
            return;
        }

        if ($entity->getOrganization() !== null) {
            return;
        }

        $org = $this->tenantContext->getOrganization();
        if ($org !== null) {
            $entity->setOrganization($org);
        }
    }
}
