<?php

namespace App\Controller\Api;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

#[Route('/api/v1/user')]
class UserController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/profile', name: 'api_user_profile_get', methods: ['GET'])]
    public function profile(): JsonResponse
    {
        // Временно берём первого пользователя из БД как "текущего"
        $user = $this->em->getRepository(User::class)->findOneBy([]) ?? $this->createDemoUser();

        return $this->json([
            'id' => 'user-' . $user->getId(),
            'email' => $user->getEmail(),
            'name' => $user->getName(),
            'phone' => $user->getPhone(),
            'avatar_url' => $user->getAvatarUrl(),
            'bonus_points' => $user->getBonusPoints(),
            'created_at' => $user->getCreatedAt()->format('Y-m-d\TH:i:s'),
        ]);
    }

    #[Route('/profile', name: 'api_user_profile_update', methods: ['PUT'])]
    public function updateProfile(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];

        $user = $this->em->getRepository(User::class)->findOneBy([]) ?? $this->createDemoUser();

        if (isset($data['email'])) {
            $user->setEmail($data['email']);
        }
        if (isset($data['name'])) {
            $user->setName($data['name']);
        }
        if (isset($data['phone'])) {
            $user->setPhone($data['phone']);
        }

        $this->em->persist($user);
        $this->em->flush();

        return $this->json([
            'id' => 'user-' . $user->getId(),
            'email' => $user->getEmail(),
            'name' => $user->getName(),
            'phone' => $user->getPhone(),
            'avatar_url' => $user->getAvatarUrl(),
            'bonus_points' => $user->getBonusPoints(),
            'created_at' => $user->getCreatedAt()->format('Y-m-d\TH:i:s'),
        ]);
    }

    private function createDemoUser(): User
    {
        $user = (new User())
            ->setEmail('user@example.com')
            ->setName('Антон')
            ->setPhone('+7 922 222-22-22')
            ->setBonusPoints(150);

        $this->em->persist($user);
        $this->em->flush();

        return $user;
    }
}

