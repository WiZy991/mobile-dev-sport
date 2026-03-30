<?php

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\Training;
use App\Entity\User;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/trainings')]
class TrainingController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
    ) {}

    #[Route('', name: 'api_trainings_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $repo = $this->em->getRepository(Training::class);

        $dateFilter = $request->query->get('date');
        $typeFilter = $request->query->get('type');

        if ($dateFilter) {
            try {
                $date = new \DateTimeImmutable($dateFilter);
                $start = $date->setTime(0, 0);
                $end = $start->modify('+1 day');
                $trainings = $repo->findBy(
                    [],
                    ['startAt' => 'ASC']
                );
                $trainings = array_filter($trainings, fn (Training $t) => $t->getStartAt() >= $start && $t->getStartAt() < $end);
            } catch (\Throwable) {
                $trainings = $repo->findBy([], ['startAt' => 'ASC']);
            }
        } else {
            $trainings = $repo->findBy([], ['startAt' => 'ASC']);
        }

        if ($typeFilter !== null && $typeFilter !== '') {
            $trainings = array_filter($trainings, fn (Training $t) => $t->getType() === $typeFilter);
        }

        $user = $this->userResolver->resolve($request);
        $currentUserId = $user?->getId();

        // Подтягиваем бронирования текущего пользователя, чтобы отметить is_booked
        $bookedTrainingIds = [];
        if ($currentUserId !== null) {
            $bookingRepo = $this->em->getRepository(Booking::class);
            /** @var Booking[] $userBookings */
            $userBookings = $bookingRepo->findBy([
                'user' => $user,
            ]);

            foreach ($userBookings as $booking) {
                if ($booking->getStatus() !== 'cancelled') {
                    $bookedTrainingIds[$booking->getTraining()->getId()] = true;
                }
            }
        }

        $data = array_map(
            fn (Training $t) => self::serializeTraining(
                $t,
                $currentUserId,
                isset($bookedTrainingIds[$t->getId()])
            ),
            $trainings
        );

        return $this->json($data);
    }

    #[Route('/{id}', name: 'api_trainings_show', methods: ['GET'])]
    public function show(string $id): JsonResponse
    {
        $numericId = str_starts_with($id, 'training-') ? (int) substr($id, 9) : (int) $id;

        /** @var Training|null $training */
        $training = $this->em->getRepository(Training::class)->find($numericId);

        if (!$training) {
            return $this->json(['error' => 'Not found'], 404);
        }

        return $this->json(self::serializeTraining($training, null, false));
    }

    private static function serializeTraining(Training $t, ?int $currentUserId, bool $isBooked): array
    {
        $start = $t->getStartAt();
        $end = $t->getEndAt();

        $trainer = $t->getTrainer();

        return [
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
            'is_booked' => $isBooked,
            'intensity' => null,
            'image_url' => null,
        ];
    }
}

