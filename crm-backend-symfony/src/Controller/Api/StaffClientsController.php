<?php

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\StaffUser;
use App\Entity\Subscription;
use App\Entity\SupportTicket;
use App\Entity\User;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\CurrentStaffUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffClientsController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('/clients', name: 'api_staff_clients', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->requireClientsAccess($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        $q = mb_strtolower(trim((string) $request->query->get('q', '')));
        $qb = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->orderBy('u.name', 'ASC')
            ->setMaxResults(80);

        if ($q !== '') {
            $qb->andWhere('LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR u.phone LIKE :qPhone')
                ->setParameter('q', '%' . $q . '%')
                ->setParameter('qPhone', '%' . $q . '%');
        }

        $clients = [];
        foreach ($qb->getQuery()->getResult() as $client) {
            if ($client instanceof User) {
                $clients[] = $client;
            }
        }

        $activeIds = $this->clientIdsWithActiveBooking($user, $clients);

        $items = [];
        foreach ($clients as $client) {
            $items[] = $this->serializeSummary($client) + [
                'hasActiveBooking' => \in_array($client->getId(), $activeIds, true),
            ];
        }

        return $this->json(['items' => $items]);
    }

    /**
     * Клиенты с предстоящей неотменённой записью; для тренера — только записи к нему
     * (та же логика видимости, что в detail(), пункт 47 репорта).
     *
     * @param list<User> $clients
     * @return list<int>
     */
    private function clientIdsWithActiveBooking(StaffUser $user, array $clients): array
    {
        if ($clients === []) {
            return [];
        }

        $qb = $this->em->createQueryBuilder()
            ->select('DISTINCT IDENTITY(b.user) AS uid')
            ->from(Booking::class, 'b')
            ->join('b.training', 't')
            ->where('b.user IN (:users)')
            ->andWhere('b.status != :cancelled')
            ->andWhere('t.startAt >= :now')
            ->setParameter('users', $clients)
            ->setParameter('cancelled', 'cancelled')
            ->setParameter('now', new \DateTimeImmutable());

        if (!$this->isPrivileged($user)) {
            $linkedTrainer = $user->getTrainer();
            if ($linkedTrainer === null) {
                return [];
            }
            $qb->andWhere('t.trainer = :trainer')
                ->setParameter('trainer', $linkedTrainer);
        }

        $ids = [];
        foreach ($qb->getQuery()->getScalarResult() as $row) {
            $ids[] = (int) $row['uid'];
        }

        return $ids;
    }

    private function isPrivileged(StaffUser $user): bool
    {
        return \in_array('ROLE_SUPER_ADMIN', $user->getRoles(), true)
            || \in_array('ROLE_ADMIN', $user->getRoles(), true)
            || \in_array('ROLE_MANAGER', $user->getRoles(), true);
    }

    #[Route('/clients/{id}', name: 'api_staff_client_detail', methods: ['GET'], requirements: ['id' => '\\d+'])]
    public function detail(int $id, Request $request): JsonResponse
    {
        $user = $this->requireClientsAccess($request);
        if ($user instanceof JsonResponse) {
            return $user;
        }

        $client = $this->em->find(User::class, $id);
        if (!$client instanceof User) {
            return $this->json(['error' => 'Not found', 'code' => 'not_found'], 404);
        }

        $activeSub = $this->em->createQueryBuilder()
            ->select('s')
            ->from(Subscription::class, 's')
            ->where('s.user = :user')
            ->setParameter('user', $client)
            ->orderBy('s.id', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        $bookingsQb = $this->em->createQueryBuilder()
            ->select('b', 't')
            ->from(Booking::class, 'b')
            ->join('b.training', 't')
            ->where('b.user = :user')
            ->andWhere('b.status != :cancelled')
            ->setParameter('user', $client)
            ->setParameter('cancelled', 'cancelled')
            ->orderBy('t.startAt', 'DESC')
            ->setMaxResults(10);

        // Тренер видит только записи клиента к себе (включая завершённые),
        // но не записи к другим тренерам (пункт 47 репорта).
        $bookings = [];
        if ($this->isPrivileged($user)) {
            $bookings = $bookingsQb->getQuery()->getResult();
        } else {
            $linkedTrainer = $user->getTrainer();
            if ($linkedTrainer !== null) {
                $bookings = $bookingsQb
                    ->andWhere('t.trainer = :trainer')
                    ->setParameter('trainer', $linkedTrainer)
                    ->getQuery()
                    ->getResult();
            }
        }

        $bookingRows = [];
        foreach ($bookings as $booking) {
            if (!$booking instanceof Booking) {
                continue;
            }
            $training = $booking->getTraining();
            // Технический статус (confirmed и т.п.) не показываем, пока нет
            // реальной механики подтверждения записи (пункт 55 репорта).
            $bookingRows[] = [
                'title' => $training->getName(),
                'meta' => $training->getStartAt()->format('d.m.Y H:i'),
            ];
        }

        $tickets = $this->em->createQueryBuilder()
            ->select('st')
            ->from(SupportTicket::class, 'st')
            ->where('st.user = :user')
            ->setParameter('user', $client)
            ->orderBy('st.createdAt', 'DESC')
            ->setMaxResults(5)
            ->getQuery()
            ->getResult();

        $ticketRows = [];
        foreach ($tickets as $ticket) {
            if (!$ticket instanceof SupportTicket) {
                continue;
            }
            $ticketRows[] = [
                'subject' => $ticket->getSubject(),
                'status' => $ticket->getStatus(),
                'createdAt' => $ticket->getCreatedAt()->format('d.m.Y H:i'),
            ];
        }

        return $this->json([
            'client' => $this->serializeSummary($client) + [
                'bonusPoints' => $client->getBonusPoints(),
                'isBlocked' => $client->isBlocked(),
                'subscription' => $activeSub instanceof Subscription ? [
                    'plan' => $activeSub->getPlan()->getName(),
                    'status' => $activeSub->getStatus(),
                    'endDate' => $activeSub->getEndDate()?->format('d.m.Y'),
                    'visitsUsed' => $activeSub->getVisitsUsed(),
                    'visitsTotal' => $activeSub->getVisitsTotal(),
                ] : null,
                'recentBookings' => $bookingRows,
                'recentTickets' => $ticketRows,
            ],
        ]);
    }

    private function requireClientsAccess(Request $request): StaffUser|JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }
        if (!$this->adminMenuBuilder->isSectionAllowed($user, 'clients')) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }

        return $user;
    }

    /** @return array<string, mixed> */
    private function serializeSummary(User $client): array
    {
        return [
            'id' => $client->getId(),
            'name' => $client->getName(),
            'email' => $client->getEmail(),
            'phone' => $client->getPhone(),
        ];
    }
}
