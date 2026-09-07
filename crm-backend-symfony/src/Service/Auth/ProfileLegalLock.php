<?php

declare(strict_types=1);

namespace App\Service\Auth;

use App\Entity\Subscription;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class ProfileLegalLock
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function isLocked(User $user): bool
    {
        if ($user->isPassportLockedFromClientEdit()) {
            return true;
        }

        $today = new \DateTimeImmutable('today');
        $count = (int) $this->em->createQueryBuilder()
            ->select('COUNT(s.id)')
            ->from(Subscription::class, 's')
            ->where('s.user = :user')
            ->andWhere('s.status IN (:st)')
            ->andWhere('(s.endDate IS NULL OR s.endDate >= :today)')
            ->setParameter('user', $user)
            ->setParameter('st', ['active', 'frozen'])
            ->setParameter('today', $today)
            ->getQuery()
            ->getSingleScalarResult();

        return $count > 0;
    }
}
