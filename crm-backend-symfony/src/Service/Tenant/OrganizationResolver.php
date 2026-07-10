<?php

declare(strict_types=1);

namespace App\Service\Tenant;

use App\Entity\Organization;
use App\Entity\StaffUser;
use App\Entity\User;
use App\Repository\OrganizationRepository;
use App\Service\CurrentUserResolver;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\HttpFoundation\RequestStack;

final class OrganizationResolver
{
    public function __construct(
        private readonly Security $security,
        private readonly RequestStack $requestStack,
        private readonly OrganizationRepository $organizations,
        private readonly CurrentUserResolver $currentUserResolver,
        private readonly string $defaultOrganizationSlug = 'demo',
    ) {
    }

    public function resolve(): ?Organization
    {
        $staff = $this->security->getUser();
        if ($staff instanceof StaffUser && $staff->getOrganization() !== null) {
            return $staff->getOrganization();
        }

        $request = $this->requestStack->getCurrentRequest();
        if ($request !== null) {
            $slug = trim((string) $request->headers->get('X-Organization-Slug', ''));
            if ($slug !== '') {
                $org = $this->organizations->findOneBySlug($slug);
                if ($org !== null) {
                    return $org;
                }
            }

            $mobileUser = $this->currentUserResolver->resolve($request);
            if ($mobileUser instanceof User && $mobileUser->getOrganization() !== null) {
                return $mobileUser->getOrganization();
            }
        }

        if ($this->defaultOrganizationSlug !== '') {
            return $this->organizations->findOneBySlug($this->defaultOrganizationSlug);
        }

        return null;
    }
}
