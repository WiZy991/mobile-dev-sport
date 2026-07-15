<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\Booking;
use App\Entity\Notification;
use App\Entity\Training;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class TrainingScheduleNotifier
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientNotificationService $clientNotifications,
        private readonly ClientNotificationScheduler $notificationScheduler,
    ) {
    }

    public function notifyTrainingUpdated(Training $training, \DateTimeImmutable $previousStartAt): void
    {
        if ($training->getStartAt()->getTimestamp() === $previousStartAt->getTimestamp()) {
            return;
        }

        $title = 'Изменение в расписании';
        $body = sprintf(
            'Тренировка «%s» перенесена на %s.',
            $training->getName(),
            $training->getStartAt()->format('d.m.Y H:i')
        );
        $referenceId = 'schedule-change-' . $training->getId() . '-' . $training->getStartAt()->format('YmdHi');

        $this->notifyBookedUsers($training, $title, $body, $referenceId);
        $this->notificationScheduler->rescheduleTrainingReminders($training);
    }

    public function notifyTrainingCancelled(Training $training): void
    {
        $title = 'Тренировка отменена';
        $body = sprintf(
            'Тренировка «%s» (%s) отменена.',
            $training->getName(),
            $training->getStartAt()->format('d.m.Y H:i')
        );
        $referenceId = 'schedule-cancel-' . $training->getId();

        $this->notifyBookedUsers($training, $title, $body, $referenceId);
        $this->notificationScheduler->cancelAllTrainingReminders($training);
    }

    private function notifyBookedUsers(Training $training, string $title, string $body, string $referenceId): void
    {
        $bookings = $this->em->createQueryBuilder()
            ->select('b')
            ->from(Booking::class, 'b')
            ->where('b.training = :training')
            ->andWhere('b.status IN (:statuses)')
            ->setParameter('training', $training)
            ->setParameter('statuses', ['confirmed', 'waiting'])
            ->getQuery()
            ->getResult();

        $seen = [];
        foreach ($bookings as $booking) {
            if (!$booking instanceof Booking) {
                continue;
            }
            $user = $booking->getUser();
            if (!$user instanceof User) {
                continue;
            }
            $userId = $user->getId();
            if ($userId === null || isset($seen[$userId])) {
                continue;
            }
            $seen[$userId] = true;

            $this->clientNotifications->notify(
                $user,
                Notification::TYPE_SCHEDULE_CHANGE,
                $title,
                $body,
                $referenceId . '-u' . $userId,
            );
        }
    }
}
