<?php

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\Document;
use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Entity\SupportTicket;
use App\Entity\Subscription;
use App\Entity\Task;
use App\Entity\Training;
use App\Entity\Trainer;
use App\Entity\User;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Api\StaffAdminSectionData;
use App\Service\CurrentStaffUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffDataController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $currentStaffUserResolver,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly EntityManagerInterface $em,
        private readonly StaffAdminSectionData $staffAdminSectionData,
    ) {
    }

    #[Route('/app/data', name: 'api_staff_app_data', methods: ['GET'])]
    public function appData(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $sections = $this->adminMenuBuilder->buildMobileAppSections(
            $this->adminMenuBuilder->allowedSections($user)
        );

        $isPrivileged = (bool) array_intersect(
            $user->getRoles(),
            ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER'],
        );

        if ($user->isTrainerRole() && !$isPrivileged) {
            // Тренер видит только собственные показатели — без глобальных счётчиков
            // CRM (клиенты всей базы, задачи, лиды).
            $metrics = $this->trainerMetrics($user->getTrainer());
        } else {
            $metrics = [
                'clients' => $this->em->getRepository(User::class)->count([]),
                'bookings' => $this->em->getRepository(Booking::class)->count([]),
                'subscriptions' => $this->em->getRepository(Subscription::class)->count([]),
            ];
            if ($this->adminMenuBuilder->isSectionAllowed($user, 'tasks')) {
                $metrics['tasks_open'] = $this->em->getRepository(Task::class)->count(['status' => 'open']);
            }
        }
        if ($this->adminMenuBuilder->isSectionAllowed($user, 'app_support')) {
            $metrics['support_new'] = $this->em->getRepository(SupportTicket::class)->count(['status' => SupportTicket::STATUS_NEW]);
            $metrics['notifications_unread'] = (int) $this->em->createQueryBuilder()
                ->select('COUNT(n.id)')
                ->from(StaffNotification::class, 'n')
                ->where('n.staffUser = :staff')
                ->andWhere('n.readAt IS NULL')
                ->setParameter('staff', $user)
                ->getQuery()
                ->getSingleScalarResult();
        }

        return $this->json([
            'employee' => [
                'id' => $user->getId(),
                'name' => $user->getName(),
                'email' => $user->getEmail(),
                'roles' => $user->getRoles(),
            ],
            'sections' => $sections,
            'metrics' => $metrics,
        ]);
    }

    #[Route('/admin/data', name: 'api_staff_admin_data', methods: ['GET'])]
    public function adminData(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $adminSections = $this->adminMenuBuilder->allowedSections($user);

        return $this->json([
            'adminSections' => $adminSections,
            'adminMenu' => $this->adminMenuBuilder->buildFor($user),
            'widgets' => [
                ['key' => 'trainers', 'value' => $this->em->getRepository(Trainer::class)->count([])],
                ['key' => 'documents', 'value' => $this->em->getRepository(Document::class)->count([])],
                ['key' => 'staff', 'value' => $this->em->getRepository(StaffUser::class)->count([])],
            ],
            'canWrite' => $this->adminMenuBuilder->canMutateAdmin($user),
        ]);
    }

    #[Route('/section-data', name: 'api_staff_section_data', methods: ['GET'])]
    public function sectionData(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $mode = (string) $request->query->get('mode', 'work');
        $section = (string) $request->query->get('section', '');
        if ($section === '') {
            return $this->json(['error' => 'Section is required', 'code' => 'missing_section'], 400);
        }

        $allowedApp = $this->adminMenuBuilder->buildMobileAppSections($this->adminMenuBuilder->allowedSections($user));
        $allowedAdmin = $this->adminMenuBuilder->allowedSections($user);
        if ($mode === 'admin' && !in_array($section, $allowedAdmin, true)) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }
        if ($mode !== 'admin' && !in_array($section, $allowedApp, true)) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }

        return $this->json([
            'mode' => $mode,
            'section' => $section,
            'cards' => $this->sectionCards($section, $user),
        ]);
    }

    #[Route('/schedule', name: 'api_staff_schedule', methods: ['GET'])]
    public function schedule(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }
        $allowed = $this->adminMenuBuilder->buildMobileAppSections($this->adminMenuBuilder->allowedSections($user));
        if (!in_array('schedule', $allowed, true)) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }

        // Опциональный ?from=YYYY-MM-DD позволяет листать расписание за пределами
        // ближайших двух недель — как в прошлое, так и в будущее (пункт 34 репорта).
        $fromParam = trim((string) $request->query->get('from', ''));
        $from = new \DateTimeImmutable('today');
        if ($fromParam !== '' && preg_match('/^\d{4}-\d{2}-\d{2}$/', $fromParam) === 1) {
            try {
                $from = (new \DateTimeImmutable($fromParam))->setTime(0, 0);
            } catch (\Exception) {
                // некорректная дата — остаёмся на сегодняшнем окне
            }
        }
        $to = $from->modify('+14 days');

        $qb = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Training::class, 't')
            ->where('t.startAt >= :from')
            ->andWhere('t.startAt < :to')
            ->setParameter('from', $from)
            ->setParameter('to', $to)
            ->orderBy('t.startAt', 'ASC');

        if ($user->requiresTrainerRental() || $user->isTrainerRole()) {
            $isPrivileged = \in_array('ROLE_ADMIN', $user->getRoles(), true)
                || \in_array('ROLE_SUPER_ADMIN', $user->getRoles(), true)
                || \in_array('ROLE_MANAGER', $user->getRoles(), true);
            if (!$isPrivileged) {
                $linkedTrainer = $user->getTrainer();
                if ($linkedTrainer !== null) {
                    $qb->andWhere('t.trainer = :trainer')->setParameter('trainer', $linkedTrainer);
                } else {
                    // У тренера ещё нет профиля Trainer (создаётся при первой записи) —
                    // чужие занятия показывать нельзя, отдаём пустое расписание.
                    $qb->andWhere('t.id IS NULL');
                }
            }
        }

        $items = $qb->getQuery()->getResult();

        $trainingIds = [];
        foreach ($items as $training) {
            if ($training instanceof Training && $training->getId() !== null) {
                $trainingIds[] = $training->getId();
            }
        }
        $bookingsByTraining = [];
        if ($trainingIds !== []) {
            $bookings = $this->em->createQueryBuilder()
                ->select('b', 't')
                ->from(Booking::class, 'b')
                ->join('b.training', 't')
                ->where('t.id IN (:ids)')
                ->andWhere('b.status != :cancelled')
                ->setParameter('ids', $trainingIds)
                ->setParameter('cancelled', 'cancelled')
                ->getQuery()
                ->getResult();
            foreach ($bookings as $booking) {
                if (!$booking instanceof Booking) {
                    continue;
                }
                $trainingId = $booking->getTraining()->getId();
                if ($trainingId === null) {
                    continue;
                }
                $bookingsByTraining[$trainingId][] = $booking->getClientName();
            }
        }

        $result = [];
        foreach ($items as $training) {
            if (!$training instanceof Training) {
                continue;
            }
            $trainingId = $training->getId();
            $participantNames = $trainingId !== null ? ($bookingsByTraining[$trainingId] ?? []) : [];
            // Пустые персональные/допуслуги после снятия клиента не показываем
            // (новые слоты без каких-либо bookings ещё можно заполнять).
            if (
                \in_array($training->getType(), ['personal', 'extra'], true)
                && $participantNames === []
                && $training->getCurrentParticipants() === 0
                && $trainingId !== null
            ) {
                $anyBooking = $this->em->getRepository(Booking::class)->findOneBy(['training' => $training]);
                if ($anyBooking instanceof Booking) {
                    continue;
                }
            }
            $participantsLabel = $participantNames === []
                ? ($training->getCurrentParticipants() . '/' . $training->getMaxParticipants())
                : implode(', ', array_slice($participantNames, 0, 5));
            $trainerEntity = $training->getTrainer();
            $trainerLabel = $training->getTrainerName()
                ?? ($trainerEntity !== null ? $trainerEntity->getName() : 'Не указан');
            $bookingRows = [];
            if ($trainingId !== null) {
                $dayBookings = $this->em->getRepository(Booking::class)->findBy(
                    ['training' => $training],
                    ['id' => 'ASC']
                );
                foreach ($dayBookings as $b) {
                    if (!$b instanceof Booking || $b->getStatus() === 'cancelled') {
                        continue;
                    }
                    $bookingRows[] = [
                        'id' => 'booking-' . $b->getId(),
                        'client_name' => $b->getClientName(),
                        'client_id' => $b->getUser()?->getId() !== null ? 'user-' . $b->getUser()->getId() : null,
                        'status' => $b->getStatus(),
                    ];
                }
            }

            $result[] = [
                'id' => $trainingId !== null ? 'training-' . $trainingId : null,
                'title' => $training->getName(),
                'trainer' => $trainerLabel,
                'type' => $training->getType(),
                'date' => $training->getStartAt()->format('Y-m-d'),
                'dayLabel' => $this->formatDayLabel($training->getStartAt()),
                'startTime' => $training->getStartAt()->format('H:i'),
                'endTime' => $training->getEndAt()->format('H:i'),
                'startAt' => $training->getStartAt()->format('d.m H:i'),
                'endAt' => $training->getEndAt()->format('d.m H:i'),
                'room' => (string) ($training->getRoom() ?? '—'),
                'clientNames' => $participantNames,
                'bookings' => $bookingRows,
                'participants' => $participantsLabel,
                'maxParticipants' => $training->getMaxParticipants(),
                'currentParticipants' => $training->getCurrentParticipants(),
            ];
        }

        $days = [];
        for ($i = 0; $i < 14; $i++) {
            $day = $from->modify('+' . $i . ' days');
            $dateKey = $day->format('Y-m-d');
            $count = 0;
            foreach ($result as $row) {
                if ($row['date'] === $dateKey) {
                    $count++;
                }
            }
            $days[] = [
                'date' => $dateKey,
                'label' => $this->formatDayShortLabel($day),
                'count' => $count,
            ];
        }

        return $this->json([
            'days' => $days,
            'items' => $result,
        ]);
    }

    #[Route('/list', name: 'api_staff_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->currentStaffUserResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $section = (string) $request->query->get('section', '');
        if ($section === '') {
            return $this->json(['error' => 'Section is required', 'code' => 'missing_section'], 400);
        }

        $allowedAdmin = $this->adminMenuBuilder->allowedSections($user);
        if (!in_array($section, $allowedAdmin, true)) {
            return $this->json(['error' => 'Forbidden section', 'code' => 'forbidden_section'], 403);
        }

        return $this->json([
            'section' => $section,
            'items' => $this->staffAdminSectionData->items($section, $user),
        ]);
    }

    /** @return list<array{key: string, value: int}> */
    private function sectionCards(string $section, ?StaffUser $user = null): array
    {
        $cards = $this->staffAdminSectionData->cards($section, $user);
        $out = [];
        foreach ($cards as $card) {
            $out[] = [
                'key' => $card['key'],
                'value' => (int) round((float) $card['value']),
            ];
        }

        return $out;
    }

    /**
     * Метрики главной для тренера: только его записи и клиенты.
     *
     * @return array<string, int>
     */
    private function trainerMetrics(?Trainer $trainer): array
    {
        if ($trainer === null) {
            return ['my_bookings_today' => 0, 'my_bookings_week' => 0, 'my_clients_active' => 0];
        }

        $today = new \DateTimeImmutable('today');

        return [
            'my_bookings_today' => $this->countTrainerBookingsBetween($trainer, $today, $today->modify('+1 day')),
            'my_bookings_week' => $this->countTrainerBookingsBetween($trainer, $today, $today->modify('+7 days')),
            'my_clients_active' => $this->countTrainerActiveClients($trainer),
        ];
    }

    private function countTrainerBookingsBetween(Trainer $trainer, \DateTimeImmutable $from, \DateTimeImmutable $to): int
    {
        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(b.id)')
            ->from(Booking::class, 'b')
            ->join('b.training', 't')
            ->where('b.status != :cancelled')
            ->andWhere('t.trainer = :trainer')
            ->andWhere('t.startAt >= :from')
            ->andWhere('t.startAt < :to')
            ->setParameter('cancelled', 'cancelled')
            ->setParameter('trainer', $trainer)
            ->setParameter('from', $from)
            ->setParameter('to', $to)
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function countTrainerActiveClients(Trainer $trainer): int
    {
        return (int) $this->em->createQueryBuilder()
            ->select('COUNT(DISTINCT IDENTITY(b.user))')
            ->from(Booking::class, 'b')
            ->join('b.training', 't')
            ->where('b.status != :cancelled')
            ->andWhere('t.trainer = :trainer')
            ->andWhere('t.startAt >= :now')
            ->setParameter('cancelled', 'cancelled')
            ->setParameter('trainer', $trainer)
            ->setParameter('now', new \DateTimeImmutable())
            ->getQuery()
            ->getSingleScalarResult();
    }

    private function formatDayLabel(\DateTimeImmutable $date): string
    {
        $weekdays = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
        $months = ['', 'января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
        $w = (int) $date->format('w');
        $m = (int) $date->format('n');

        return $weekdays[$w] . ', ' . $date->format('j') . ' ' . $months[$m];
    }

    private function formatDayShortLabel(\DateTimeImmutable $date): string
    {
        $weekdays = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];

        return $weekdays[(int) $date->format('w')] . ' ' . $date->format('j');
    }
}
