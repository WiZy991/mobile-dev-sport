<?php

namespace App\Controller\Api;

use App\Entity\GuestPass;
use App\Entity\Subscription;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/guest-passes')]
class GuestPassController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_guest_passes_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $passes = $this->em->getRepository(GuestPass::class)->findBy(
            ['owner' => $user],
            ['createdAt' => 'DESC']
        );

        return $this->json(array_map(self::serialize(...), $passes));
    }

    #[Route('', name: 'api_guest_passes_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        if (!$this->userCanCreateGuestPass($user)) {
            return $this->json(['error' => 'Нет активного абонемента для создания гостевого пропуска'], 403);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $guestName = trim((string) ($data['guest_name'] ?? $data['guestName'] ?? ''));

        $pass = (new GuestPass())
            ->setOwner($user)
            ->setGuestName($guestName !== '' ? $guestName : null);

        $this->em->persist($pass);
        $this->em->flush();

        return $this->json(self::serialize($pass), 201);
    }

    private function userCanCreateGuestPass($user): bool
    {
        $today = new \DateTimeImmutable('today');
        $subs = $this->em->getRepository(Subscription::class)->findBy(
            ['user' => $user, 'status' => 'active']
        );
        foreach ($subs as $sub) {
            if ($sub->coversCalendarDay($today)) {
                return true;
            }
        }
        return false;
    }

    private static function serialize(GuestPass $p): array
    {
        return [
            'id' => 'guest-pass-' . $p->getId(),
            'guest_name' => $p->getGuestName(),
            'status' => $p->getStatus(),
            'created_at' => $p->getCreatedAt()->format('Y-m-d\TH:i:s'),
            'used_at' => $p->getUsedAt()?->format('Y-m-d\TH:i:s'),
            'qr_code_data' => 'FITNESSCLUB:GUEST:' . $p->getId() . ':' . $p->getQrToken(),
        ];
    }
}
