<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\StaffUser;
use App\Service\CurrentStaffUserResolver;
use App\Service\Staff\StaffOnboardingService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffOnboardingController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $staffResolver,
        private readonly StaffOnboardingService $onboarding,
    ) {
    }

    #[Route('/onboarding', name: 'api_staff_onboarding', methods: ['GET'])]
    public function onboarding(Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        return $this->json($this->onboarding->serialize($user));
    }
}
