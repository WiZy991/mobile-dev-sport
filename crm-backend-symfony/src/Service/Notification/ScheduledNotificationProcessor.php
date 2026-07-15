<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\ScheduledClientNotification;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Отправляет наступившие отложенные уведомления.
 * Вызывается при API-запросах и heartbeat шлюза (без cron).
 */
final class ScheduledNotificationProcessor
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientNotificationService $clientNotifications,
    ) {
    }

    public function processDue(int $limit = 40): int
    {
        try {
            return $this->doProcessDue($limit);
        } catch (\Throwable) {
            return 0;
        }
    }

    private function doProcessDue(int $limit): int
    {
        $now = new \DateTimeImmutable();

        /** @var list<ScheduledClientNotification> $due */
        $due = $this->em->createQueryBuilder()
            ->select('s')
            ->from(ScheduledClientNotification::class, 's')
            ->andWhere('s.status = :pending')
            ->andWhere('s.notifyAt <= :now')
            ->setParameter('pending', ScheduledClientNotification::STATUS_PENDING)
            ->setParameter('now', $now)
            ->orderBy('s.notifyAt', 'ASC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();

        $sent = 0;
        foreach ($due as $row) {
            $this->clientNotifications->notify(
                $row->getUser(),
                $row->getType(),
                $row->getTitle(),
                $row->getBody(),
                $row->getReferenceId(),
            );

            $row
                ->setStatus(ScheduledClientNotification::STATUS_SENT)
                ->setSentAt($now);
            ++$sent;
        }

        if ($sent > 0) {
            $this->em->flush();
        }

        return $sent;
    }
}
