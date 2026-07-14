<?php

declare(strict_types=1);

namespace App\EventListener;

use App\Entity\Booking;
use App\Entity\Feedback;
use App\Entity\Lead;
use App\Entity\Sale;
use App\Entity\Task;
use App\Entity\StaffNotification;
use App\Service\Staff\StaffEventNotifier;
use Doctrine\Bundle\DoctrineBundle\Attribute\AsDoctrineListener;
use Doctrine\ORM\Event\PostPersistEventArgs;
use Doctrine\ORM\Events;

#[AsDoctrineListener(event: Events::postPersist)]
final class StaffNotificationEntityListener
{
    public function __construct(
        private readonly StaffEventNotifier $staffEventNotifier,
    ) {
    }

    public function postPersist(PostPersistEventArgs $args): void
    {
        $entity = $args->getObject();

        if ($entity instanceof Lead) {
            $this->onLeadCreated($entity);

            return;
        }

        if ($entity instanceof Booking) {
            $this->onBookingCreated($entity);

            return;
        }

        if ($entity instanceof Feedback) {
            $this->onFeedbackCreated($entity);

            return;
        }

        if ($entity instanceof Sale) {
            $this->onSaleCreated($entity);

            return;
        }

        if ($entity instanceof Task) {
            $this->onTaskCreated($entity);
        }
    }

    private function onLeadCreated(Lead $lead): void
    {
        $name = $lead->getName() ?: 'Без имени';
        $phone = $lead->getPhone() ?: '';
        $source = $lead->getSource() ?: 'unknown';

        $this->staffEventNotifier->notifyBySection(
            'leads',
            StaffNotification::TYPE_LEAD,
            'Новый лид',
            sprintf('%s (%s) — источник: %s', $name, $phone, $source),
            $lead->getId() !== null ? (string) $lead->getId() : null,
        );
    }

    private function onBookingCreated(Booking $booking): void
    {
        $status = $booking->getStatus();
        if (!in_array($status, ['confirmed', 'waiting'], true)) {
            return;
        }

        $training = $booking->getTraining();
        $client = $booking->getClientName() ?: ($booking->getUser()?->getName() ?? 'Клиент');
        $trainingName = $training->getName();
        $when = $training->getStartAt()->format('d.m.Y H:i');
        $statusLabel = $status === 'waiting' ? 'лист ожидания' : 'запись';

        $this->staffEventNotifier->notifyBySection(
            'bookings',
            StaffNotification::TYPE_BOOKING,
            'Новая запись на тренировку',
            sprintf('%s: %s — «%s» (%s)', $client, $statusLabel, $trainingName, $when),
            $booking->getId() !== null ? (string) $booking->getId() : null,
        );
    }

    private function onFeedbackCreated(Feedback $feedback): void
    {
        $userName = $feedback->getUser()?->getName() ?? 'Клиент';
        $rating = $feedback->getRating();
        $comment = $feedback->getComment();
        $body = sprintf('%s: оценка %d/5', $userName, $rating);
        if ($comment !== null && $comment !== '') {
            $body .= ' — ' . mb_substr($comment, 0, 100);
        }

        $this->staffEventNotifier->notifyBySection(
            'app_support',
            StaffNotification::TYPE_FEEDBACK,
            'Отзыв из приложения',
            $body,
            $feedback->getId() !== null ? (string) $feedback->getId() : null,
        );
    }

    private function onSaleCreated(Sale $sale): void
    {
        $client = $sale->getClientName() ?: ($sale->getUser()?->getName() ?? 'Клиент');
        $product = $sale->getProductName();
        $total = number_format($sale->getTotal(), 0, '.', ' ');

        $this->staffEventNotifier->notifyBySection(
            'sales',
            StaffNotification::TYPE_SALE,
            'Новая продажа',
            sprintf('%s: %s — %s ₽', $client, $product, $total),
            $sale->getId() !== null ? (string) $sale->getId() : null,
        );
    }

    private function onTaskCreated(Task $task): void
    {
        $title = $task->getTitle() ?: 'Без названия';
        $client = $task->getClientName() ?: ($task->getClient()?->getName() ?? '');

        $body = $client !== '' ? sprintf('%s — клиент: %s', $title, $client) : $title;

        $this->staffEventNotifier->notifyBySection(
            'tasks',
            StaffNotification::TYPE_TASK,
            'Новая задача',
            $body,
            $task->getId() !== null ? (string) $task->getId() : null,
        );
    }
}
