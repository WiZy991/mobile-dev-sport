<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\StaffUser;
use App\Entity\Training;
use App\Entity\User;
use App\Service\Booking\BookingWriteService;
use App\Service\CurrentStaffUserResolver;
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
    ) {
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
}
