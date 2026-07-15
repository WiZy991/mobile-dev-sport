<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\Notification;
use App\Entity\Promotion;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class PromoBroadcastNotifier
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientNotificationService $clientNotifications,
    ) {
    }

    public function notifyPromotionActivated(Promotion $promotion): void
    {
        if (!$promotion->isActive()) {
            return;
        }

        $promotionId = $promotion->getId();
        if ($promotionId === null) {
            return;
        }

        $title = $promotion->getTitle() ?: 'Новая акция';
        $body = trim((string) ($promotion->getSubtitle() ?: 'Специальное предложение клуба'));
        $referenceId = 'promo-' . $promotionId;

        $users = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('u.isBlocked = false')
            ->andWhere('u.notifyPromo = true')
            ->setMaxResults(3000)
            ->getQuery()
            ->getResult();

        foreach ($users as $user) {
            if (!$user instanceof User) {
                continue;
            }
            $this->clientNotifications->notify(
                $user,
                Notification::TYPE_PROMO,
                $title,
                $body,
                $referenceId,
            );
        }
    }
}
