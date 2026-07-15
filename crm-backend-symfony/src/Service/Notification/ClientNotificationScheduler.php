<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\Booking;
use App\Entity\Notification;
use App\Entity\ScheduledClientNotification;
use App\Entity\Subscription;
use App\Entity\Training;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;

/**
 * Планирует отложенные уведомления в момент события (запись, покупка, смена расписания).
 * Отправка — через {@see ScheduledNotificationProcessor} (без cron).
 */
final class ClientNotificationScheduler
{
    /** За сколько минут до тренировки напоминать (по порядку). */
    private const TRAINING_REMINDER_MINUTES = [60, 10];

    /** За сколько дней до окончания абонемента напоминать. */
    private const SUBSCRIPTION_EXPIRY_DAYS = [7, 1];

    public function __construct(
        private readonly EntityManagerInterface $em,
        #[Autowire(lazy: true)]
        private readonly ScheduledNotificationProcessor $processor,
    ) {
    }

    public function scheduleTrainingRemindersForBooking(Booking $booking): void
    {
        if ($booking->getStatus() !== 'confirmed') {
            return;
        }

        $user = $booking->getUser();
        $training = $booking->getTraining();
        if (!$user instanceof User || !$training instanceof Training) {
            return;
        }

        $this->cancelTrainingReminders($user, $training);

        $startAt = $training->getStartAt();
        $room = trim((string) ($training->getRoom() ?? '')) ?: 'зале';
        foreach (self::TRAINING_REMINDER_MINUTES as $minutesBefore) {
            $notifyAt = $startAt->modify(sprintf('-%d minutes', $minutesBefore));
            if ($notifyAt <= new \DateTimeImmutable()) {
                continue;
            }

            $referenceId = sprintf(
                'training-reminder-%d-u%d-%dm',
                $training->getId(),
                $user->getId(),
                $minutesBefore,
            );

            $title = $minutesBefore <= 15 ? 'Тренировка скоро начнётся' : 'Напоминание о тренировке';
            $body = sprintf(
                'Через %d мин начинается «%s» (%s) в %s.',
                $minutesBefore,
                $training->getName(),
                $startAt->format('H:i'),
                $room,
            );

            $this->upsertPending($user, Notification::TYPE_TRAINING_REMINDER, $title, $body, $referenceId, $notifyAt);
        }

        $this->em->flush();
        $this->processor->processDue(10);
    }

    public function cancelTrainingReminders(User $user, Training $training): void
    {
        $userId = $user->getId();
        $trainingId = $training->getId();
        if ($userId === null || $trainingId === null) {
            return;
        }

        $prefix = sprintf('training-reminder-%d-u%d-', $trainingId, $userId);
        $this->cancelByReferencePrefix($prefix);
    }

    public function rescheduleTrainingReminders(Training $training): void
    {
        $bookings = $this->em->getRepository(Booking::class)->findBy([
            'training' => $training,
            'status' => 'confirmed',
        ]);

        foreach ($bookings as $booking) {
            if ($booking instanceof Booking) {
                $this->scheduleTrainingRemindersForBooking($booking);
            }
        }
    }

    public function cancelAllTrainingReminders(Training $training): void
    {
        $trainingId = $training->getId();
        if ($trainingId === null) {
            return;
        }

        $prefix = sprintf('training-reminder-%d-', $trainingId);
        $this->cancelByReferencePrefix($prefix);
        $this->em->flush();
    }

    public function scheduleSubscriptionExpiryReminders(Subscription $subscription): void
    {
        $user = $subscription->getUser();
        $endDate = $subscription->getEndDate();
        $subId = $subscription->getId();
        if (!$user instanceof User || $endDate === null || $subId === null || $subscription->getStatus() !== 'active') {
            return;
        }

        $this->cancelSubscriptionExpiryReminders($subscription);

        $planName = $subscription->getPlan()->getName();
        foreach (self::SUBSCRIPTION_EXPIRY_DAYS as $daysBefore) {
            $notifyAt = $endDate->modify(sprintf('-%d days', $daysBefore))->setTime(9, 0);
            if ($notifyAt <= new \DateTimeImmutable()) {
                continue;
            }

            $referenceId = sprintf('subscription-expiry-%d-%dd', $subId, $daysBefore);
            $title = $daysBefore <= 1 ? 'Абонемент заканчивается завтра' : 'Абонемент скоро закончится';
            $body = sprintf(
                'До окончания абонемента «%s» осталось %d дн. Продлите в приложении.',
                $planName,
                $daysBefore,
            );

            $this->upsertPending($user, Notification::TYPE_SUBSCRIPTION, $title, $body, $referenceId, $notifyAt);
        }

        $this->em->flush();
        $this->processor->processDue(10);
    }

    public function cancelSubscriptionExpiryReminders(Subscription $subscription): void
    {
        $subId = $subscription->getId();
        if ($subId === null) {
            return;
        }

        $prefix = sprintf('subscription-expiry-%d-', $subId);
        $this->cancelByReferencePrefix($prefix);
    }

    private function upsertPending(
        User $user,
        string $type,
        string $title,
        string $body,
        string $referenceId,
        \DateTimeImmutable $notifyAt,
    ): void {
        $repo = $this->em->getRepository(ScheduledClientNotification::class);
        $existing = $repo->findOneBy(['referenceId' => $referenceId]);
        if ($existing instanceof ScheduledClientNotification) {
            if ($existing->getStatus() === ScheduledClientNotification::STATUS_SENT) {
                return;
            }
            $existing
                ->setUser($user)
                ->setType($type)
                ->setTitle($title)
                ->setBody($body)
                ->setNotifyAt($notifyAt)
                ->setStatus(ScheduledClientNotification::STATUS_PENDING)
                ->setSentAt(null);

            return;
        }

        $row = (new ScheduledClientNotification())
            ->setUser($user)
            ->setType($type)
            ->setTitle($title)
            ->setBody($body)
            ->setReferenceId($referenceId)
            ->setNotifyAt($notifyAt)
            ->setStatus(ScheduledClientNotification::STATUS_PENDING);

        $this->em->persist($row);
    }

    private function cancelByReferencePrefix(string $prefix): void
    {
        /** @var list<ScheduledClientNotification> $rows */
        $rows = $this->em->createQueryBuilder()
            ->select('s')
            ->from(ScheduledClientNotification::class, 's')
            ->andWhere('s.referenceId LIKE :prefix')
            ->andWhere('s.status = :pending')
            ->setParameter('prefix', $prefix . '%')
            ->setParameter('pending', ScheduledClientNotification::STATUS_PENDING)
            ->getQuery()
            ->getResult();

        foreach ($rows as $row) {
            $row->setStatus(ScheduledClientNotification::STATUS_CANCELLED);
        }
    }
}
