<?php

namespace App\Controller\Api;

use App\Entity\StaffPushToken;
use App\Entity\StaffUser;
use App\Service\CurrentStaffUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffPushController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('/push-token', name: 'api_staff_push_token', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $tokenValue = trim((string) ($data['token'] ?? ''));
        $platform = trim((string) ($data['platform'] ?? 'android'));
        if ($tokenValue === '') {
            return $this->json(['error' => 'Token is required', 'code' => 'missing_token'], 400);
        }

        $repo = $this->em->getRepository(StaffPushToken::class);
        $pushToken = $repo->findOneBy(['token' => $tokenValue]);
        if (!$pushToken instanceof StaffPushToken) {
            $pushToken = (new StaffPushToken())
                ->setStaffUser($user)
                ->setToken($tokenValue)
                ->setPlatform($platform);
            $this->em->persist($pushToken);
        } else {
            $pushToken
                ->setStaffUser($user)
                ->setPlatform($platform)
                ->touch();
        }
        $this->em->flush();

        return $this->json(['success' => true]);
    }
}
