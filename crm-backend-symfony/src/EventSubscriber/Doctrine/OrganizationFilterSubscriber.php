<?php

declare(strict_types=1);

namespace App\EventSubscriber\Doctrine;

use App\Service\Tenant\TenantContext;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\RequestEvent;
use Symfony\Component\HttpKernel\KernelEvents;

final class OrganizationFilterSubscriber implements EventSubscriberInterface
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly TenantContext $tenantContext,
    ) {
    }

    public static function getSubscribedEvents(): array
    {
        return [
            KernelEvents::REQUEST => ['onKernelRequest', 5],
        ];
    }

    public function onKernelRequest(RequestEvent $event): void
    {
        if (!$event->isMainRequest()) {
            return;
        }

        $org = $this->tenantContext->getOrganization();
        if ($org !== null && !$this->tenantContext->isPlatformAdmin()) {
            $this->tenantContext->setOrganization($org);
        }

        if (!$this->tenantContext->shouldApplyTenantFilter()) {
            return;
        }

        $orgId = $this->tenantContext->getOrganizationId();
        if ($orgId === null) {
            return;
        }

        $filter = $this->em->getFilters()->enable('organization');
        $filter->setParameter('organization_id', (string) $orgId);
    }
}
