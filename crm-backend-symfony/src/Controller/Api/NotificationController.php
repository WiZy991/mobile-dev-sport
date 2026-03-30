<?php

namespace App\Controller\Api;

use App\Entity\Notification;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/notifications')]
class NotificationController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_notifications_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $notifications = $this->em->getRepository(Notification::class)->findBy(
            ['user' => $user],
            ['createdAt' => 'DESC'],
            50
        );

        $data = array_map(self::serialize(...), $notifications);
        return $this->json($data);
    }

    #[Route('/{id}/read', name: 'api_notifications_mark_read', methods: ['POST'])]
    public function markRead(string $id, Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $numericId = str_starts_with($id, 'notification-') ? (int) substr($id, 13) : (int) $id;
        /** @var Notification|null $notification */
        $notification = $this->em->getRepository(Notification::class)->find($numericId);

        if (!$notification || $notification->getUser()->getId() !== $user->getId()) {
            return $this->json(['error' => 'Notification not found'], 404);
        }

        if (!$notification->isRead()) {
            $notification->setReadAt(new \DateTimeImmutable());
            $this->em->persist($notification);
            $this->em->flush();
        }

        return $this->json(['success' => true]);
    }

    #[Route('/read-all', name: 'api_notifications_mark_all_read', methods: ['POST'])]
    public function markAllRead(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $notifications = $this->em->getRepository(Notification::class)->findBy(
            ['user' => $user, 'readAt' => null]
        );

        $now = new \DateTimeImmutable();
        foreach ($notifications as $n) {
            $n->setReadAt($now);
            $this->em->persist($n);
        }
        $this->em->flush();

        return $this->json(['success' => true]);
    }

    private static function serialize(Notification $n): array
    {
        return [
            'id' => 'notification-' . $n->getId(),
            'type' => $n->getType(),
            'title' => $n->getTitle(),
            'message' => $n->getBody(),
            'created_at' => $n->getCreatedAt()->format('Y-m-d\TH:i:s'),
            'is_read' => $n->isRead(),
            'reference_id' => $n->getReferenceId(),
        ];
    }
}
