<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\Club;
use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Очередь абонементов клиента в одном клубе: купленный заранее начинает действовать
 * на следующий день после окончания текущего. Ожидающий абонемент хранится как
 * active с будущей start_date — турникет его не пускает до даты старта (coversCalendarDay),
 * а API отдаёт статус pending.
 */
final class SubscriptionQueueService
{
    private const PERSONAL_TYPE = 'personal';

    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** Дата старта нового абонемента: сейчас или день после окончания действующих в том же клубе. */
    public function startDateFor(User $user, ?Club $club, SubscriptionPlan $plan, ?\DateTimeImmutable $now = null): \DateTimeImmutable
    {
        $now ??= new \DateTimeImmutable();
        if ($plan->getType() === self::PERSONAL_TYPE) {
            return $now;
        }

        $lastEnd = $this->lastEndDate($this->queueCandidates($user, $club), $now);

        return $lastEnd === null ? $now : $lastEnd->modify('+1 day')->setTime(0, 0);
    }

    public static function isWaiting(Subscription $subscription, ?\DateTimeImmutable $today = null): bool
    {
        $today ??= new \DateTimeImmutable('today');

        return $subscription->getStatus() === 'active'
            && $subscription->getStartDate()->format('Y-m-d') > $today->format('Y-m-d');
    }

    /**
     * Текущий абонемент стал длиннее (заморозка/продление) — ожидающие после него сдвигаются на столько же.
     */
    public function shiftAfter(Subscription $base, \DateTimeImmutable $previousEnd, int $days): void
    {
        if ($days === 0) {
            return;
        }
        foreach ($this->waitingAfter($base, $previousEnd) as $next) {
            $this->moveBy($next, $days);
        }
    }

    /**
     * Текущий закончился раньше срока (посещения исчерпаны, отмена) — ожидающий стартует сразу,
     * если в клубе не осталось другого действующего абонемента.
     */
    public function pullForwardAfter(Subscription $finished): void
    {
        $user = $finished->getUser();
        $now = new \DateTimeImmutable();
        $today = $now->format('Y-m-d');

        $all = $this->queueCandidates($user, $finished->getClub());
        $waiting = [];
        $current = [];
        foreach ($all as $sub) {
            if ($sub === $finished) {
                continue;
            }
            if (self::isWaiting($sub, $now)) {
                $waiting[] = $sub;
            } elseif ($sub->getStartDate()->format('Y-m-d') <= $today) {
                $current[] = $sub;
            }
        }
        if ($waiting === []) {
            return;
        }

        usort($waiting, static fn (Subscription $a, Subscription $b): int => $a->getStartDate() <=> $b->getStartDate());
        $lastEnd = $this->lastEndDate($current, $now);
        $newStart = $lastEnd === null ? new \DateTimeImmutable('today') : $lastEnd->modify('+1 day')->setTime(0, 0);
        $delta = self::daysBetween($waiting[0]->getStartDate(), $newStart);
        if ($delta >= 0) {
            return;
        }
        foreach ($waiting as $sub) {
            $this->moveBy($sub, $delta);
        }
    }

    /** @return list<Subscription> */
    private function waitingAfter(Subscription $base, \DateTimeImmutable $previousEnd): array
    {
        $after = $previousEnd->format('Y-m-d');
        $result = [];
        foreach ($this->queueCandidates($base->getUser(), $base->getClub()) as $sub) {
            if ($sub === $base || !self::isWaiting($sub)) {
                continue;
            }
            if ($sub->getStartDate()->format('Y-m-d') > $after) {
                $result[] = $sub;
            }
        }

        return $result;
    }

    /**
     * Действующие и ожидающие абонементы клиента в клубе (без персональных тренировок).
     *
     * @return list<Subscription>
     */
    private function queueCandidates(User $user, ?Club $club): array
    {
        $qb = $this->em->createQueryBuilder()
            ->select('s', 'p')
            ->from(Subscription::class, 's')
            ->join('s.plan', 'p')
            ->where('s.user = :user')
            ->andWhere('s.status IN (:statuses)')
            ->andWhere('p.type != :personal')
            ->setParameter('user', $user)
            ->setParameter('statuses', ['active', 'frozen'])
            ->setParameter('personal', self::PERSONAL_TYPE);
        if ($club !== null) {
            $qb->andWhere('(s.club = :club OR s.club IS NULL)')->setParameter('club', $club);
        }

        return $qb->getQuery()->getResult();
    }

    /**
     * Самая поздняя дата окончания среди ещё не закончившихся. Бессрочный абонемент
     * (без end_date) очередь не задаёт — новый тогда стартует сразу, как раньше.
     *
     * @param list<Subscription> $subs
     */
    private function lastEndDate(array $subs, \DateTimeImmutable $now): ?\DateTimeImmutable
    {
        $today = $now->format('Y-m-d');
        $lastEnd = null;
        foreach ($subs as $sub) {
            $end = $sub->getEndDate();
            if ($end === null || $end->format('Y-m-d') < $today) {
                continue;
            }
            if ($sub->getVisitsTotal() !== null && !$sub->hasRemainingVisits()) {
                continue;
            }
            if ($lastEnd === null || $end > $lastEnd) {
                $lastEnd = $end;
            }
        }

        return $lastEnd;
    }

    private function moveBy(Subscription $sub, int $days): void
    {
        $modifier = sprintf('%+d days', $days);
        $sub->setStartDate($sub->getStartDate()->modify($modifier));
        if ($sub->getEndDate() !== null) {
            $sub->setEndDate($sub->getEndDate()->modify($modifier));
        }
    }

    private static function daysBetween(\DateTimeImmutable $from, \DateTimeImmutable $to): int
    {
        $a = new \DateTimeImmutable($from->format('Y-m-d'));
        $b = new \DateTimeImmutable($to->format('Y-m-d'));

        return (int) $a->diff($b)->format('%r%a');
    }
}
