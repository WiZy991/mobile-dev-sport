<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\StaffUser;
use App\Entity\Training;
use App\Entity\User;
use App\Service\Booking\BookingWriteService;
use App\Service\CurrentStaffUserResolver;
use App\Service\Staff\StaffOnboardingService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff')]
final class StaffBookingController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentStaffUserResolver $staffResolver,
        private readonly BookingWriteService $bookingWrite,
        private readonly StaffOnboardingService $onboarding,
    ) {
    }

    #[Route('/trainings', name: 'api_staff_training_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $staff = $this->staffResolver->resolve($request);
        if (!$staff instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $isPrivileged = \in_array('ROLE_SUPER_ADMIN', $staff->getRoles(), true)
            || \in_array('ROLE_ADMIN', $staff->getRoles(), true)
            || \in_array('ROLE_MANAGER', $staff->getRoles(), true);
        if (!$staff->isTrainerRole() && !$isPrivileged) {
            return $this->json(['error' => 'Создавать занятия может только тренер или администратор', 'code' => 'forbidden'], 403);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $name = trim((string) ($data['name'] ?? ''));
        $type = (string) ($data['type'] ?? 'personal');
        if (!\in_array($type, ['group', 'personal', 'extra'], true)) {
            $type = 'personal';
        }
        $room = isset($data['room']) ? trim((string) $data['room']) : null;
        if ($room === '') {
            $room = null;
        }
        $maxParticipants = (int) ($data['max_participants'] ?? ($type === 'personal' ? 1 : 10));
        if ($type === 'personal') {
            $maxParticipants = 1;
        }
        $maxParticipants = max(1, $maxParticipants);

        $startRaw = (string) ($data['start_at'] ?? '');
        $endRaw = (string) ($data['end_at'] ?? '');
        try {
            $startAt = new \DateTimeImmutable($startRaw);
            $endAt = $endRaw !== '' ? new \DateTimeImmutable($endRaw) : $startAt->modify('+1 hour');
        } catch (\Exception) {
            return $this->json(['error' => 'Укажите корректные start_at / end_at', 'code' => 'invalid_datetime'], 400);
        }
        if ($endAt <= $startAt) {
            return $this->json(['error' => 'Время окончания должно быть позже начала', 'code' => 'invalid_range'], 400);
        }
        if ($name === '') {
            $name = match ($type) {
                'group' => 'Групповое занятие',
                'extra' => 'Дополнительная услуга',
                default => 'Персональная тренировка',
            };
        }

        $trainer = $staff->getTrainer();
        if ($trainer === null && ($staff->isTrainerRole() || !$isPrivileged)) {
            try {
                $trainer = $this->onboarding->ensureTrainerProfile($staff);
            } catch (\Throwable $e) {
                return $this->json(['error' => $e->getMessage(), 'code' => 'trainer_missing'], 400);
            }
        }
        if ($trainer === null && !$isPrivileged) {
            return $this->json(['error' => 'Нет карточки тренера', 'code' => 'trainer_missing'], 400);
        }

        $training = (new Training())
            ->setName($name)
            ->setDescription(match ($type) {
                'personal' => 'Персональное занятие',
                'extra' => 'Дополнительная услуга',
                default => 'Групповое занятие',
            })
            ->setType($type)
            ->setRoom($room)
            ->setStartAt($startAt)
            ->setEndAt($endAt)
            ->setMaxParticipants($maxParticipants)
            ->setCurrentParticipants(0);

        if ($trainer !== null) {
            $training->setTrainer($trainer)->setTrainerName($trainer->getName());
            if ($trainer->getOrganization() !== null) {
                $training->setOrganization($trainer->getOrganization());
            }
        } elseif ($staff->getOrganization() !== null) {
            $training->setOrganization($staff->getOrganization());
        }

        $this->em->persist($training);
        $this->em->flush();

        $bookingPayload = null;
        $clientIdRaw = $data['client_id'] ?? null;
        if ($clientIdRaw !== null && $clientIdRaw !== '') {
            $clientId = is_string($clientIdRaw) && str_starts_with($clientIdRaw, 'user-')
                ? (int) substr($clientIdRaw, 5)
                : (int) $clientIdRaw;
            $client = $this->em->getRepository(User::class)->find($clientId);
            if (!$client instanceof User) {
                return $this->json([
                    'error' => 'Занятие создано, но клиент не найден',
                    'code' => 'client_not_found',
                    'training' => $this->serializeTraining($training),
                ], 201);
            }
            try {
                $booking = $this->bookingWrite->bookClient($training, $client, 'confirmed');
                $bookingPayload = [
                    'id' => 'booking-' . $booking->getId(),
                    'client_id' => 'user-' . $client->getId(),
                    'client_name' => $client->getName(),
                    'status' => $booking->getStatus(),
                ];
                $this->em->refresh($training);
            } catch (\RuntimeException $e) {
                return $this->json([
                    'error' => 'Занятие создано, но запись клиента не удалась: ' . $e->getMessage(),
                    'code' => 'book_failed',
                    'training' => $this->serializeTraining($training),
                ], 201);
            }
        }

        return $this->json([
            'training' => $this->serializeTraining($training),
            'booking' => $bookingPayload,
        ], 201);
    }

    #[Route('/trainings/{id}/book', name: 'api_staff_training_book', methods: ['POST'])]
    public function book(string $id, Request $request): JsonResponse
    {
        $staff = $this->staffResolver->resolve($request);
        if (!$staff instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $numericId = str_starts_with($id, 'training-') ? (int) substr($id, 9) : (int) $id;
        $training = $this->em->getRepository(Training::class)->find($numericId);
        if (!$training instanceof Training) {
            return $this->json(['error' => 'Training not found'], 404);
        }

        if (!$this->canManageTraining($staff, $training)) {
            return $this->json(['error' => 'Можно записывать только на свои занятия', 'code' => 'forbidden_training'], 403);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $clientIdRaw = $data['client_id'] ?? $request->request->get('client_id');
        $clientId = is_string($clientIdRaw) && str_starts_with($clientIdRaw, 'user-')
            ? (int) substr($clientIdRaw, 5)
            : (int) $clientIdRaw;

        $client = $this->em->getRepository(User::class)->find($clientId);
        if (!$client instanceof User) {
            return $this->json(['error' => 'Client not found', 'code' => 'client_not_found'], 404);
        }

        try {
            $booking = $this->bookingWrite->bookClient($training, $client, 'confirmed');
        } catch (\RuntimeException $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'book_failed'], 409);
        }

        return $this->json([
            'id' => 'booking-' . $booking->getId(),
            'status' => $booking->getStatus(),
            'client_id' => 'user-' . $client->getId(),
            'client_name' => $client->getName(),
            'training_id' => 'training-' . $training->getId(),
        ], 201);
    }

    #[Route('/bookings/{id}', name: 'api_staff_booking_cancel', methods: ['DELETE'])]
    public function cancel(string $id, Request $request): JsonResponse
    {
        $staff = $this->staffResolver->resolve($request);
        if (!$staff instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $numericId = str_starts_with($id, 'booking-') ? (int) substr($id, 8) : (int) $id;
        $booking = $this->em->getRepository(Booking::class)->find($numericId);
        if (!$booking instanceof Booking) {
            return $this->json(['error' => 'Booking not found'], 404);
        }

        if (!$this->canManageTraining($staff, $booking->getTraining())) {
            return $this->json(['error' => 'Можно снимать запись только со своих занятий', 'code' => 'forbidden_training'], 403);
        }

        $this->bookingWrite->cancelBooking($booking, notifyStaff: false);

        return $this->json(['success' => true]);
    }

    private function canManageTraining(StaffUser $staff, Training $training): bool
    {
        foreach (['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER'] as $role) {
            if (\in_array($role, $staff->getRoles(), true)) {
                return true;
            }
        }

        $linked = $staff->getTrainer();
        if ($linked === null) {
            return false;
        }

        return $training->getTrainer()?->getId() === $linked->getId();
    }

    /** @return array<string, mixed> */
    private function serializeTraining(Training $training): array
    {
        $id = $training->getId();

        return [
            'id' => $id !== null ? 'training-' . $id : null,
            'title' => $training->getName(),
            'trainer' => $training->getTrainerName() ?? $training->getTrainer()?->getName(),
            'type' => $training->getType(),
            'date' => $training->getStartAt()->format('Y-m-d'),
            'startTime' => $training->getStartAt()->format('H:i'),
            'endTime' => $training->getEndAt()->format('H:i'),
            'start_at' => $training->getStartAt()->format(\DateTimeInterface::ATOM),
            'end_at' => $training->getEndAt()->format(\DateTimeInterface::ATOM),
            'room' => (string) ($training->getRoom() ?? '—'),
            'maxParticipants' => $training->getMaxParticipants(),
            'currentParticipants' => $training->getCurrentParticipants(),
            'bookings' => [],
            'clientNames' => [],
        ];
    }
}
