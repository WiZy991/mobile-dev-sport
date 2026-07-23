<?php

declare(strict_types=1);

namespace App\Service\Booking;

use App\Entity\Booking;
use App\Entity\Notification;
use App\Entity\StaffNotification;
use App\Entity\Training;
use App\Entity\User;
use App\Service\Notification\ClientNotificationScheduler;
use App\Service\Notification\ClientNotificationService;
use App\Service\Staff\StaffEventNotifier;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Общая запись клиента на тренировку (клиентский book раньше; теперь staff assign).
 */
final class BookingWriteService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientNotificationService $clientNotifications,
        private readonly ClientNotificationScheduler $notificationScheduler,
        private readonly StaffEventNotifier $staffEventNotifier,
    ) {
    }

    public function bookClient(Training $training, User $client, string $status = 'confirmed'): Booking
    {
        $existing = $this->em->getRepository(Booking::class)->findOneBy([
            'training' => $training,
            'user' => $client,
        ]);
        if ($existing instanceof Booking && $existing->getStatus() !== 'cancelled') {
            throw new \RuntimeException('Клиент уже записан на это занятие');
        }

        if ($status === 'confirmed'
            && $training->getCurrentParticipants() >= $training->getMaxParticipants()
        ) {
            throw new \RuntimeException('Нет свободных мест');
        }

        $booking = (new Booking())
            ->setTraining($training)
            ->setClientName($client->getName())
            ->setUser($client)
            ->setStatus($status);
        $org = $training->getOrganization()
            ?? $client->getOrganization()
            ?? $training->getTrainer()?->getOrganization();
        if ($org !== null) {
            $booking->setOrganization($org);
        }

        if ($status === 'confirmed') {
            $training->setCurrentParticipants($training->getCurrentParticipants() + 1);
            $this->em->persist($training);
        }

        $this->em->persist($booking);
        $this->em->flush();

        $trainingName = $training->getName();
        $when = $training->getStartAt()->format('d.m.Y H:i');
        $this->clientNotifications->notify(
            $client,
            $status === 'waiting' ? Notification::TYPE_BOOKING_CONFIRMED : Notification::TYPE_BOOKING_CONFIRMED,
            $status === 'waiting' ? 'Лист ожидания' : 'Запись подтверждена',
            $status === 'waiting'
                ? sprintf('Вы в листе ожидания на «%s» (%s)', $trainingName, $when)
                : sprintf('Вы записаны на «%s» (%s)', $trainingName, $when),
            'booking-' . $booking->getId(),
        );
        if ($status === 'confirmed') {
            $this->notificationScheduler->scheduleTrainingRemindersForBooking($booking);
        }

        return $booking;
    }

    public function cancelBooking(Booking $booking, bool $notifyStaff = true): void
    {
        $training = $booking->getTraining();
        $bookingUser = $booking->getUser();
        if ($bookingUser instanceof User) {
            $this->notificationScheduler->cancelTrainingReminders($bookingUser, $training);
        }

        if ($booking->getStatus() === 'confirmed') {
            $training->setCurrentParticipants(max(0, $training->getCurrentParticipants() - 1));
            $this->em->persist($training);
        }

        $booking->setStatus('cancelled');
        $this->em->persist($booking);
        $this->em->flush();

        $trainingName = $training->getName();
        $when = $training->getStartAt()->format('d.m.Y H:i');
        if ($bookingUser instanceof User) {
            $this->clientNotifications->notify(
                $bookingUser,
                Notification::TYPE_BOOKING_CANCELLED,
                'Запись отменена',
                sprintf('Ваша запись на «%s» (%s) отменена.', $trainingName, $when),
                'booking-' . $booking->getId(),
            );
        }

        if ($notifyStaff) {
            $client = $booking->getClientName() ?: ($bookingUser?->getName() ?? 'Клиент');
            $this->staffEventNotifier->notifyBySection(
                'bookings',
                StaffNotification::TYPE_BOOKING,
                'Отмена записи на тренировку',
                sprintf('%s — отмена записи на «%s» (%s)', $client, $trainingName, $when),
                $booking->getId() !== null ? (string) $booking->getId() : null,
            );
        }
    }
}
