<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Generator\UrlGeneratorInterface;

/**
 * JSON для колокола уведомлений в админке: опрос staff_notifications и desktop-уведомлений.
 */
final class AdminStaffNotificationController extends AbstractController
{
    private const LIST_LIMIT = 40;

    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function poll(Request $request): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof StaffUser) {
            return new JsonResponse([
                'enabled' => false,
                'latest_id' => 0,
                'unread_count' => 0,
                'items' => [],
                'new_items' => [],
            ]);
        }

        $sinceId = max(0, (int) $request->query->get('since_id', 0));

        $recent = $this->fetchNotifications($user, null);
        $latestId = 0;
        foreach ($recent as $row) {
            $id = $row->getId();
            if ($id !== null && $id > $latestId) {
                $latestId = $id;
            }
        }

        $newItems = $sinceId > 0 ? $this->fetchNotifications($user, $sinceId) : [];

        return new JsonResponse([
            'enabled' => true,
            'latest_id' => $latestId,
            'unread_count' => $this->countUnread($user),
            'items' => array_map(fn (StaffNotification $n) => $this->serialize($n), $recent),
            'new_items' => array_map(fn (StaffNotification $n) => $this->serialize($n), $newItems),
        ]);
    }

    public function readAll(Request $request): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof StaffUser) {
            return new JsonResponse(['success' => false], 401);
        }
        if (!$this->isCsrfTokenValid('crm_notifications', (string) $request->headers->get('X-CSRF-Token', ''))) {
            return new JsonResponse(['success' => false, 'error' => 'Invalid CSRF'], 403);
        }

        $rows = $this->em->createQueryBuilder()
            ->select('n')
            ->from(StaffNotification::class, 'n')
            ->where('n.staffUser = :staff')
            ->andWhere('n.readAt IS NULL')
            ->setParameter('staff', $user)
            ->getQuery()
            ->getResult();

        $now = new \DateTimeImmutable();
        foreach ($rows as $row) {
            if ($row instanceof StaffNotification) {
                $row->setReadAt($now);
            }
        }
        $this->em->flush();

        return new JsonResponse(['success' => true]);
    }

    public function read(int $id, Request $request): JsonResponse
    {
        $user = $this->getUser();
        if (!$user instanceof StaffUser) {
            return new JsonResponse(['success' => false], 401);
        }
        if (!$this->isCsrfTokenValid('crm_notifications', (string) $request->headers->get('X-CSRF-Token', ''))) {
            return new JsonResponse(['success' => false, 'error' => 'Invalid CSRF'], 403);
        }

        $notification = $this->em->find(StaffNotification::class, $id);
        if (!$notification instanceof StaffNotification || $notification->getStaffUser()->getId() !== $user->getId()) {
            return new JsonResponse(['success' => false, 'error' => 'Not found'], 404);
        }

        if (!$notification->isRead()) {
            $notification->setReadAt(new \DateTimeImmutable());
            $this->em->flush();
        }

        return new JsonResponse(['success' => true]);
    }

    /** @return list<StaffNotification> */
    private function fetchNotifications(StaffUser $user, ?int $sinceId): array
    {
        $qb = $this->em->getRepository(StaffNotification::class)
            ->createQueryBuilder('n')
            ->where('n.staffUser = :staff')
            ->setParameter('staff', $user)
            ->orderBy('n.id', 'DESC')
            ->setMaxResults(self::LIST_LIMIT);

        if ($sinceId !== null && $sinceId > 0) {
            $qb->andWhere('n.id > :since')
                ->setParameter('since', $sinceId)
                ->orderBy('n.id', 'ASC');
        }

        /** @var list<StaffNotification> */
        return $qb->getQuery()->getResult();
    }

    private function countUnread(StaffUser $user): int
    {
        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(n.id)')
            ->from(StaffNotification::class, 'n')
            ->where('n.staffUser = :staff')
            ->andWhere('n.readAt IS NULL')
            ->setParameter('staff', $user)
            ->getQuery()
            ->getSingleScalarResult();
    }

    /** @return array<string, mixed> */
    private function serialize(StaffNotification $notification): array
    {
        return [
            'id' => $notification->getId(),
            'type' => $notification->getType(),
            'title' => $notification->getTitle(),
            'body' => $notification->getBody(),
            'reference_id' => $notification->getReferenceId(),
            'is_read' => $notification->isRead(),
            'created_at' => $notification->getCreatedAt()->format(\DateTimeInterface::ATOM),
            'url' => $this->resolveUrl($notification),
        ];
    }

    private function resolveUrl(StaffNotification $notification): string
    {
        $ref = $notification->getReferenceId();

        return match ($notification->getType()) {
            StaffNotification::TYPE_SUPPORT_TICKET => $this->generateUrl(
                'admin_section',
                ['section' => 'app_support'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_ACCESS_ALARM => $ref !== null
                ? $this->generateUrl('admin_franchise_list', [], UrlGeneratorInterface::ABSOLUTE_PATH)
                : $this->generateUrl('admin_franchise_list', [], UrlGeneratorInterface::ABSOLUTE_PATH),
            StaffNotification::TYPE_LEAD => $this->generateUrl(
                'admin_section',
                ['section' => 'leads'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_BOOKING => $this->generateUrl(
                'admin_section',
                ['section' => 'bookings'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_SALE,
            StaffNotification::TYPE_PAYMENT,
            StaffNotification::TYPE_SUBSCRIPTION => $this->generateUrl(
                'admin_section',
                ['section' => 'sales'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_FEEDBACK => $this->generateUrl(
                'admin_section',
                ['section' => 'comments'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_CLIENT => $ref !== null
                ? $this->generateUrl('admin_client_show', ['id' => (int) $ref], UrlGeneratorInterface::ABSOLUTE_PATH)
                : $this->generateUrl('admin_section', ['section' => 'clients'], UrlGeneratorInterface::ABSOLUTE_PATH),
            StaffNotification::TYPE_STAFF_REGISTRATION => $ref !== null
                ? $this->generateUrl('admin_crm_staff_edit', ['id' => (int) $ref], UrlGeneratorInterface::ABSOLUTE_PATH)
                : $this->generateUrl('admin_crm_staff_index', ['filter' => 'pending'], UrlGeneratorInterface::ABSOLUTE_PATH),
            StaffNotification::TYPE_GUEST_PASS => $this->generateUrl(
                'admin_section',
                ['section' => 'visits'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            StaffNotification::TYPE_TASK => $this->generateUrl(
                'admin_section',
                ['section' => 'tasks'],
                UrlGeneratorInterface::ABSOLUTE_PATH
            ),
            default => $this->generateUrl('admin_dashboard', [], UrlGeneratorInterface::ABSOLUTE_PATH),
        };
    }
}
