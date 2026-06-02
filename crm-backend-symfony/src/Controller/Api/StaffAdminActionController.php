<?php

namespace App\Controller\Api;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\CurrentStaffUserResolver;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff/admin')]
final class StaffAdminActionController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    #[Route('/action-check', name: 'api_staff_admin_action_check', methods: ['POST'])]
    public function actionCheck(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $action = (string) ($data['action'] ?? '');
        $section = (string) ($data['section'] ?? '');

        if (!in_array($action, ['read', 'write'], true)) {
            return $this->json(['error' => 'Unknown action', 'code' => 'unknown_action'], 400);
        }
        if ($section === '' || !$this->adminMenuBuilder->isSectionAllowed($user, $section)) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }
        if ($action === 'write' && !$this->adminMenuBuilder->canMutateAdmin($user)) {
            return $this->json(['error' => 'Forbidden action', 'code' => 'forbidden_action'], 403);
        }

        return $this->json([
            'allowed' => true,
            'action' => $action,
            'section' => $section,
        ]);
    }
}
