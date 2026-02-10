<?php

namespace App\Controller\Api;

use App\Entity\PushToken;
use App\Entity\User;
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

        $repo = $this->em->getRepository(PushToken::class);

        /** @var PushToken|null $pushToken */
        $pushToken = $repo->findOneBy(['token' => $tokenValue]);

        if (!$pushToken) {
            $pushToken = (new PushToken())
                ->setToken($tokenValue)
                ->setPlatform($platform);

            // Временно считаем "текущим" первого пользователя в БД
            $user = $this->em->getRepository(User::class)->findOneBy([]);
            if ($user) {
                $pushToken->setUser($user);
            }

            $this->em->persist($pushToken);
        } else {
            $pushToken
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
}

