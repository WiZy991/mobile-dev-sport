<?php

namespace App\Controller\Api;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\CurrentStaffUserResolver;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffConfigController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    #[Route('/config', name: 'api_staff_config', methods: ['GET'])]
    public function config(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $adminSections = $this->adminMenuBuilder->allowedSections($user);
        $appSections = $this->adminMenuBuilder->buildMobileAppSections($adminSections);
        $adminActions = $this->buildAdminActions($user);

        return $this->json([
            'roles' => $user->getRoles(),
            'appSections' => $appSections,
            'adminSections' => $adminSections,
            'adminActions' => $adminActions,
            'featureFlags' => [
                'adminModuleEnabled' => $adminSections !== [],
                'canMutateAdmin' => in_array('admin.write', $adminActions, true),
            ],
        ]);
    }

    /** @return list<string> */
    private function buildAdminActions(StaffUser $user): array
    {
        $actions = ['admin.read'];
        if ($this->adminMenuBuilder->canMutateAdmin($user)) {
            $actions[] = 'admin.write';
        }
        if ($this->adminMenuBuilder->hasSupportRole($user)) {
            $actions[] = 'support.write';
        }
        if ($this->adminMenuBuilder->hasTrainerRole($user)) {
            $actions[] = 'admin.training.write';
        }

        return $actions;
    }
}
