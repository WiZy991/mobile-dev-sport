<?php

namespace App\Controller\Api;

use App\Entity\PushToken;
use App\Entity\User;
use App\Service\CurrentUserResolver;
use App\Service\Notification\ClientNotificationService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/user')]
class PushTokenController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly ClientNotificationService $clientNotifications,
    ) {}

    #[Route('/push-token', name: 'api_user_push_token', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];

        $tokenValue = $data['token'] ?? null;
        $platform = $data['platform'] ?? 'android';

        if (!$tokenValue) {
            return $this->json(['error' => 'Token is required'], 400);
        }

        $user = $this->userResolver->resolve($request);
        if (!$user instanceof User) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }
        if (!$user->isNotifyPushEnabled()) {
            return $this->json(['error' => 'Push notifications disabled', 'code' => 'push_disabled'], 403);
        }

        $repo = $this->em->getRepository(PushToken::class);

        /** @var PushToken|null $pushToken */
        $pushToken = $repo->findOneBy(['token' => $tokenValue]);

        if (!$pushToken) {
            $pushToken = (new PushToken())
                ->setToken($tokenValue)
                ->setPlatform($platform)
                ->setUser($user);
            $this->em->persist($pushToken);
        } else {
            $pushToken
                ->setUser($user)
                ->setPlatform($platform)
                ->touch();
        }

        $this->em->flush();

        return $this->json([
            'success' => true,
            'token' => $pushToken->getToken(),
            'platform' => $pushToken->getPlatform(),
        ]);
    }

    #[Route('/push-token', name: 'api_user_push_token_delete', methods: ['DELETE'])]
    public function unregister(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user instanceof User) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $this->clientNotifications->clearPushTokens($user);

        return $this->json(['success' => true]);
    }
}
