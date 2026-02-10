<?php

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\Training;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1')]
class BookingController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/bookings', name: 'api_bookings_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        // Временно берём первого пользователя как "текущего"
        $user = $this->em->getRepository(User::class)->findOneBy([]);

        $criteria = $user ? ['user' => $user] : [];

        $bookings = $this->em->getRepository(Booking::class)->findBy($criteria, ['id' => 'DESC']);

        $data = array_map(self::serializeBooking(...), $bookings);

        return $this->json($data);
    }

    #[Route('/trainings/{id}/book', name: 'api_bookings_book', methods: ['POST'])]
    public function book(string $id, Request $request): JsonResponse
    {
        $numericId = str_starts_with($id, 'training-') ? (int) substr($id, 9) : (int) $id;

        /** @var Training|null $training */
        $training = $this->em->getRepository(Training::class)->find($numericId);

        if (!$training) {
            return $this->json(['error' => 'Training not found'], 404);
        }

        $clientName = (string) ($request->request->get('client_name') ?: 'Мобильный клиент');

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->findOneBy([]);

        $booking = (new Booking())
            ->setTraining($training)
            ->setClientName($clientName)
            ->setUser($user)
            ->setStatus('confirmed');

        // увеличиваем счётчик записавшихся, если бронь подтверждённая
        $training->setCurrentParticipants($training->getCurrentParticipants() + 1);

        $this->em->persist($booking);
        $this->em->persist($training);
        $this->em->flush();

        return $this->json(self::serializeBooking($booking));
    }

    #[Route('/trainings/{id}/waiting-list', name: 'api_bookings_waiting_list', methods: ['POST'])]
    public function waitingList(string $id, Request $request): JsonResponse
    {
        $numericId = str_starts_with($id, 'training-') ? (int) substr($id, 9) : (int) $id;

        /** @var Training|null $training */
        $training = $this->em->getRepository(Training::class)->find($numericId);

        if (!$training) {
            return $this->json(['error' => 'Training not found'], 404);
        }

        $clientName = (string) ($request->request->get('client_name') ?: 'Мобильный клиент');

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->findOneBy([]);

        $booking = (new Booking())
            ->setTraining($training)
            ->setClientName($clientName)
            ->setUser($user)
            ->setStatus('waiting');

        $this->em->persist($booking);
        $this->em->flush();

        return $this->json(self::serializeBooking($booking));
    }

    #[Route('/bookings/{id}', name: 'api_bookings_cancel', methods: ['DELETE'])]
    public function cancel(string $id): JsonResponse
    {
        $numericId = str_starts_with($id, 'booking-') ? (int) substr($id, 8) : (int) $id;

        /** @var Booking|null $booking */
        $booking = $this->em->getRepository(Booking::class)->find($numericId);

        if ($booking) {
            // если отменяем подтверждённую бронь — уменьшаем счётчик участников
            if ($booking->getStatus() === 'confirmed') {
                $training = $booking->getTraining();
                $training->setCurrentParticipants(
                    max(0, $training->getCurrentParticipants() - 1)
                );
                $this->em->persist($training);
            }

            $booking->setStatus('cancelled');
            $this->em->persist($booking);
            $this->em->flush();
        }

        return $this->json(['success' => true]);
    }

    private static function serializeBooking(Booking $b): array
    {
        $t = $b->getTraining();
        $start = $t->getStartAt();
        $end = $t->getEndAt();

        $trainer = $t->getTrainer();

        return [
            'id' => 'booking-' . $b->getId(),
            'status' => $b->getStatus(),
            'booked_at' => $b->getBookedAt()->format('Y-m-d\TH:i:s'),
            'training' => [
                'id' => 'training-' . $t->getId(),
                'name' => $t->getName(),
                'description' => $t->getDescription(),
                'type' => $t->getType(),
                'trainer' => $trainer ? [
                    'id' => 'trainer-' . $trainer->getId(),
                    'name' => $trainer->getName(),
                    'photo_url' => $trainer->getPhotoUrl(),
                    'specialization' => $trainer->getSpecialization(),
                    'rating' => $trainer->getRating(),
                ] : [
                    'id' => null,
                    'name' => $t->getTrainerName(),
                    'photo_url' => null,
                    'specialization' => null,
                    'rating' => null,
                ],
                'start_time' => $start->format('Y-m-d\TH:i:s'),
                'end_time' => $end->format('Y-m-d\TH:i:s'),
                'duration_minutes' => ($end->getTimestamp() - $start->getTimestamp()) / 60,
                'room' => $t->getRoom(),
                'max_participants' => $t->getMaxParticipants(),
                'current_participants' => $t->getCurrentParticipants(),
                'is_booked' => true,
                'intensity' => null,
                'image_url' => null,
            ],
        ];
    }
}

