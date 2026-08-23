<?php

namespace App\Controller\Api;

use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Entity\SupportTicket;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\CurrentStaffUserResolver;
use App\Service\Support\SupportTicketStaffNotifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffSupportController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly EntityManagerInterface $em,
        private readonly SupportTicketStaffNotifier $staffNotifier,
    ) {
    }

    #[Route('/support/tickets', name: 'api_staff_support_tickets', methods: ['GET'])]
    public function tickets(Request $request): JsonResponse
    {
        $user = $this->requireAppSupportAccess($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        $status = (string) $request->query->get('status', '');
        $qb = $this->em->createQueryBuilder()
            ->select('t')
            ->from(SupportTicket::class, 't')
            ->orderBy('t.createdAt', 'DESC')
            ->setMaxResults(100);
        if ($status !== '' && in_array($status, SupportTicket::allowedStatuses(), true)) {
            $qb->andWhere('t.status = :status')->setParameter('status', $status);
        }

        $items = [];
        foreach ($qb->getQuery()->getResult() as $ticket) {
            if ($ticket instanceof SupportTicket) {
                $items[] = $this->serializeTicket($ticket);
            }
        }

        return $this->json([
            'items' => $items,
            'newCount' => (int) $this->em->getRepository(SupportTicket::class)->count(['status' => SupportTicket::STATUS_NEW]),
        ]);
    }

    /** Обратная связь тренера/сотрудника в клуб (не инбокс поддержки). */
    #[Route('/feedback/tickets', name: 'api_staff_feedback_tickets_list', methods: ['GET'])]
    public function myFeedbackTickets(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $tickets = $this->em->getRepository(SupportTicket::class)->findBy(
            ['staffUser' => $user],
            ['createdAt' => 'DESC'],
            50,
        );

        return $this->json([
            'items' => array_map($this->serializeTicket(...), $tickets),
        ]);
    }

    #[Route('/feedback/tickets', name: 'api_staff_feedback_tickets_create', methods: ['POST'])]
    public function createFeedbackTicket(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $subject = mb_substr(trim((string) ($data['subject'] ?? '')), 0, 200);
        $message = trim((string) ($data['message'] ?? ''));
        $category = (string) ($data['category'] ?? SupportTicket::CATEGORY_OTHER);

        if ($subject === '') {
            return $this->json(['error' => 'Укажите тему', 'code' => 'subject_required'], 400);
        }
        if (mb_strlen($message) < 5) {
            return $this->json(['error' => 'Сообщение слишком короткое', 'code' => 'message_too_short'], 400);
        }
        if (mb_strlen($message) > 8000) {
            return $this->json(['error' => 'Сообщение слишком длинное', 'code' => 'message_too_long'], 400);
        }
        if (!in_array($category, SupportTicket::allowedCategories(), true)) {
            $category = SupportTicket::CATEGORY_OTHER;
        }

        $staffLabel = $user->getName() !== '' ? $user->getName() : $user->getEmail();
        $fullMessage = trim($message . "\n\n— " . $staffLabel . ' (' . $user->getEmail() . ')');

        $ticket = (new SupportTicket())
            ->setUser(null)
            ->setStaffUser($user)
            ->setSubject('[Тренер] ' . $subject)
            ->setMessage($fullMessage)
            ->setCategory($category)
            ->setContactEmail($user->getEmail());

        if ($user->getOrganization() !== null) {
            $ticket->setOrganization($user->getOrganization());
        }

        $this->em->persist($ticket);
        $this->em->flush();

        try {
            $this->staffNotifier->notifyNewTicket($ticket);
        } catch (\Throwable) {
        }

        return $this->json([
            'success' => true,
            'id' => $ticket->getId(),
            'ticket' => $this->serializeTicket($ticket),
        ], 201);
    }

    #[Route('/support/tickets/{id}/status', name: 'api_staff_support_ticket_status', methods: ['POST'])]
    public function updateStatus(int $id, Request $request): JsonResponse
    {
        $user = $this->requireAppSupportAccess($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }
        if (!$user instanceof StaffUser || !$this->adminMenuBuilder->canUpdateSupportTicket($user)) {
            return $this->json(['error' => 'Forbidden', 'code' => 'forbidden'], 403);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $status = (string) ($data['status'] ?? '');
        if (!in_array($status, SupportTicket::allowedStatuses(), true)) {
            return $this->json(['error' => 'Invalid status', 'code' => 'invalid_status'], 400);
        }

        $ticket = $this->em->find(SupportTicket::class, $id);
        if (!$ticket instanceof SupportTicket) {
            return $this->json(['error' => 'Not found', 'code' => 'not_found'], 404);
        }

        $ticket->setStatus($status);
        $this->em->flush();

        return $this->json(['success' => true, 'ticket' => $this->serializeTicket($ticket)]);
    }

    #[Route('/notifications', name: 'api_staff_notifications', methods: ['GET'])]
    public function notifications(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $rows = $this->em->getRepository(StaffNotification::class)->findBy(
            ['staffUser' => $user],
            ['createdAt' => 'DESC'],
            50
        );

        return $this->json([
            'items' => array_map($this->serializeNotification(...), $rows),
            'unreadCount' => (int) $this->em->createQueryBuilder()
                ->select('COUNT(n.id)')
                ->from(StaffNotification::class, 'n')
                ->where('n.staffUser = :staff')
                ->andWhere('n.readAt IS NULL')
                ->setParameter('staff', $user)
                ->getQuery()
                ->getSingleScalarResult(),
        ]);
    }

    #[Route('/notifications/read-all', name: 'api_staff_notifications_read_all', methods: ['POST'])]
    public function readAllNotifications(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
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

        return $this->json(['success' => true]);
    }

    #[Route('/notifications/{id}/read', name: 'api_staff_notification_read', methods: ['POST'])]
    public function readNotification(int $id, Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $notification = $this->em->find(StaffNotification::class, $id);
        if (!$notification instanceof StaffNotification || $notification->getStaffUser()->getId() !== $user->getId()) {
            return $this->json(['error' => 'Not found', 'code' => 'not_found'], 404);
        }

        if (!$notification->isRead()) {
            $notification->setReadAt(new \DateTimeImmutable());
            $this->em->flush();
        }

        return $this->json(['success' => true]);
    }

    private function requireAppSupportAccess(Request $request): StaffUser|JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }
        if (!$this->adminMenuBuilder->isSectionAllowed($user, 'app_support')) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }

        return $user;
    }

    /** @return array<string, mixed> */
    private function serializeTicket(SupportTicket $ticket): array
    {
        $client = $ticket->getUser();

        return [
            'id' => $ticket->getId(),
            'subject' => $ticket->getSubject(),
            'message' => $ticket->getMessage(),
            'category' => $ticket->getCategory(),
            'status' => $ticket->getStatus(),
            'contactEmail' => $ticket->getContactEmail(),
            'clientName' => $client?->getName() ?? $ticket->getStaffUser()?->getName(),
            'clientPhone' => $client?->getPhone(),
            'clientId' => $client?->getId(),
            'staffUserId' => $ticket->getStaffUser()?->getId(),
            'createdAt' => $ticket->getCreatedAt()->format('d.m.Y H:i'),
        ];
    }

    /** @return array<string, mixed> */
    private function serializeNotification(StaffNotification $notification): array
    {
        return [
            'id' => $notification->getId(),
            'type' => $notification->getType(),
            'title' => $notification->getTitle(),
            'body' => $notification->getBody(),
            'referenceId' => $notification->getReferenceId(),
            'createdAt' => $notification->getCreatedAt()->format('d.m.Y H:i'),
            'isRead' => $notification->isRead(),
        ];
    }
}
