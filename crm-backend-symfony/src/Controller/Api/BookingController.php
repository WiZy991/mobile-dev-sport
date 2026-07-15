<?php

namespace App\Controller\Api;

use App\Entity\Booking;
use App\Entity\Notification;
use App\Entity\StaffNotification;
use App\Entity\Training;
use App\Entity\User;
use App\Service\CurrentUserResolver;
use App\Service\Notification\ClientNotificationScheduler;
use App\Service\Notification\ClientNotificationService;
use App\Service\Staff\StaffEventNotifier;
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
        private readonly CurrentUserResolver $userResolver,
        private readonly StaffEventNotifier $staffEventNotifier,
        private readonly ClientNotificationService $clientNotifications,
        private readonly ClientNotificationScheduler $notificationScheduler,
    ) {}

    #[Route('/bookings', name: 'api_bookings_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $bookings = $this->em->getRepository(Booking::class)->findBy(['user' => $user], ['id' => 'DESC']);

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

        $user = $this->userResolver->resolve($request);

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

        if ($user instanceof User) {
            $trainingName = $training->getName();
            $when = $training->getStartAt()->format('d.m.Y H:i');
            $this->clientNotifications->notify(
                $user,
                Notification::TYPE_BOOKING_CONFIRMED,
                'Запись подтверждена',
                sprintf('Вы записаны на «%s» (%s)', $trainingName, $when),
                'booking-' . $booking->getId(),
            );
            $this->notificationScheduler->scheduleTrainingRemindersForBooking($booking);
        }

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

        $user = $this->userResolver->resolve($request);

        $booking = (new Booking())
            ->setTraining($training)
            ->setClientName($clientName)
            ->setUser($user)
            ->setStatus('waiting');

        $this->em->persist($booking);
        $this->em->flush();

        if ($user instanceof User) {
            $trainingName = $training->getName();
            $when = $training->getStartAt()->format('d.m.Y H:i');
            $this->clientNotifications->notify(
                $user,
                Notification::TYPE_BOOKING_CONFIRMED,
                'Лист ожидания',
                sprintf('Вы в листе ожидания на «%s» (%s). Мы уведомим, если освободится место.', $trainingName, $when),
                'booking-' . $booking->getId(),
            );
        }

        return $this->json(self::serializeBooking($booking));
    }

    #[Route('/bookings/{id}', name: 'api_bookings_cancel', methods: ['DELETE'])]
    public function cancel(string $id, Request $request): JsonResponse
    {
        $numericId = str_starts_with($id, 'booking-') ? (int) substr($id, 8) : (int) $id;

        /** @var Booking|null $booking */
        $booking = $this->em->getRepository(Booking::class)->find($numericId);

        if (!$booking) {
            return $this->json(['error' => 'Booking not found'], 404);
        }

        $user = $this->userResolver->resolve($request);
        if ($user && $booking->getUser() && $booking->getUser()->getId() !== $user->getId()) {
            return $this->json(['error' => 'Forbidden'], 403);
        }

        $training = $booking->getTraining();
        $bookingUser = $booking->getUser();
        if ($bookingUser instanceof User) {
            $this->notificationScheduler->cancelTrainingReminders($bookingUser, $training);
        }

        if ($booking->getStatus() === 'confirmed') {
            $training->setCurrentParticipants(
                max(0, $training->getCurrentParticipants() - 1)
            );
            $this->em->persist($training);

            // Notify users on waiting list that a spot opened
            $waitingBookings = $this->em->getRepository(Booking::class)->findBy(
                ['training' => $training, 'status' => 'waiting'],
                ['id' => 'ASC'],
                5
            );
            $trainingName = $training->getName();
            $startTime = $training->getStartAt()->format('d.m.Y H:i');
            foreach ($waitingBookings as $wb) {
                $wu = $wb->getUser();
                if ($wu instanceof User && $wu->getId() !== $booking->getUser()?->getId()) {
                    $this->clientNotifications->notify(
                        $wu,
                        Notification::TYPE_SPOT_FREED,
                        'Освободилось место!',
                        "Освободилось место на тренировку «{$trainingName}» ({$startTime}). Запишитесь, пока место свободно!",
                        'training-' . $training->getId() . '-u' . $wu->getId(),
                    );
                }
            }
        }

        $booking->setStatus('cancelled');
        $this->em->persist($booking);
        $this->em->flush();

        $client = $booking->getClientName() ?: ($booking->getUser()?->getName() ?? 'Клиент');
        $trainingName = $training->getName();
        $when = $training->getStartAt()->format('d.m.Y H:i');

        $bookingUser = $booking->getUser();
        if ($bookingUser instanceof User) {
            $this->clientNotifications->notify(
                $bookingUser,
                Notification::TYPE_BOOKING_CANCELLED,
                'Запись отменена',
                sprintf('Ваша запись на «%s» (%s) отменена.', $trainingName, $when),
                'booking-' . $booking->getId(),
            );
        }

        $this->staffEventNotifier->notifyBySection(
            'bookings',
            StaffNotification::TYPE_BOOKING,
            'Отмена записи на тренировку',
            sprintf('%s отменил запись на «%s» (%s)', $client, $trainingName, $when),
            $booking->getId() !== null ? (string) $booking->getId() : null,
        );

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
            'status' => self::normalizeBookingStatusForApi($b->getStatus()),
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

    private static function normalizeBookingStatusForApi(string $status): string
    {
        return $status === 'waiting' ? 'waiting_list' : $status;
    }
}

