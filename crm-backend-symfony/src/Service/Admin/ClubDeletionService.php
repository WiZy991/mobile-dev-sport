<?php

declare(strict_types=1);

namespace App\Service\Admin;

use App\Entity\AccessAlarm;
use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\GatewayCommand;
use App\Entity\Subscription;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class ClubDeletionService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function countClubs(): int
    {
        return (int) $this->em->getRepository(Club::class)->count([]);
    }

    /** @return array{users: int, subscriptions: int, active_subscriptions: int} */
    public function describeDependencies(Club $club): array
    {
        $users = (int) $this->em->getRepository(User::class)->count(['club' => $club]);
        $subscriptions = (int) $this->em->getRepository(Subscription::class)->count(['club' => $club]);
        $activeSubscriptions = (int) $this->em->getRepository(Subscription::class)->count([
            'club' => $club,
            'status' => 'active',
        ]);

        return [
            'users' => $users,
            'subscriptions' => $subscriptions,
            'active_subscriptions' => $activeSubscriptions,
        ];
    }

    public function clearGateway(Club $club): void
    {
        $this->em->createQueryBuilder()
            ->delete(GatewayCommand::class, 'c')
            ->andWhere('c.club = :club')
            ->andWhere('c.status = :status')
            ->setParameter('club', $club)
            ->setParameter('status', GatewayCommand::STATUS_PENDING)
            ->getQuery()
            ->execute();

        $club->setGatewayToken(null);
        $club->setGatewayLastSeenAt(null);
        $this->em->flush();
    }

    public function deleteClub(Club $club): void
    {
        if ($this->countClubs() <= 1) {
            throw new \RuntimeException('Нельзя удалить последний клуб в CRM.');
        }

        $clubId = $club->getId();
        if ($clubId === null) {
            throw new \RuntimeException('Клуб не найден.');
        }

        $this->em->createQueryBuilder()
            ->update(User::class, 'u')
            ->set('u.club', ':null')
            ->where('u.club = :club')
            ->setParameter('null', null)
            ->setParameter('club', $club)
            ->getQuery()
            ->execute();

        $this->em->createQueryBuilder()
            ->update(Subscription::class, 's')
            ->set('s.club', ':null')
            ->where('s.club = :club')
            ->setParameter('null', null)
            ->setParameter('club', $club)
            ->getQuery()
            ->execute();

        $this->em->createQueryBuilder()
            ->update(AccessLog::class, 'l')
            ->set('l.club', ':null')
            ->where('l.club = :club')
            ->setParameter('null', null)
            ->setParameter('club', $club)
            ->getQuery()
            ->execute();

        $this->em->createQueryBuilder()
            ->update(AccessAlarm::class, 'a')
            ->set('a.club', ':null')
            ->where('a.club = :club')
            ->setParameter('null', null)
            ->setParameter('club', $club)
            ->getQuery()
            ->execute();

        $this->em->remove($club);
        $this->em->flush();
    }
}
