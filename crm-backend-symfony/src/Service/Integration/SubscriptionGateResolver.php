<?php

namespace App\Service\Integration;

use App\Entity\Club;
use App\Entity\Subscription;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Выбор абонемента для прохода: календарь + привязка к клубу шлюза.
 */
final class SubscriptionGateResolver
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * @return array{0: ?Subscription, 1: ?string} [абонемент или null, код отказа]
     *                 Код: null при успехе; «no_active» — нет по датам/статусу; «wrong_club» — есть по датам, но не этот клуб.
     */
    public function resolveForEntry(User $user, ?Club $gateClub): array
    {
        $today = new \DateTimeImmutable('today');
        /** @var Subscription[] $subs */
        $subs = $this->em->getRepository(Subscription::class)->findBy(['user' => $user, 'status' => 'active']);

        $calendarOk = [];
        foreach ($subs as $sub) {
            if ($sub->coversCalendarDay($today)) {
                $calendarOk[] = $sub;
            }
        }
        if ($calendarOk === []) {
            return [null, 'no_active'];
        }

        if ($gateClub === null) {
            return [$calendarOk[0], null];
        }

        foreach ($calendarOk as $sub) {
            if ($sub->isValidAtClub($gateClub)) {
                return [$sub, null];
            }
        }

        return [null, 'wrong_club'];
    }
}
