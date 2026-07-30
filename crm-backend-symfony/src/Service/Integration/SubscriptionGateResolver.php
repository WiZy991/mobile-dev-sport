<?php

namespace App\Service\Integration;

use App\Entity\Club;
use App\Entity\Subscription;
use App\Entity\User;
use App\Service\Api\SubscriptionLifecycleService;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Выбор абонемента для прохода: календарь + привязка к клубу шлюза + лимит посещений.
 */
final class SubscriptionGateResolver
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionLifecycleService $lifecycle,
    ) {
    }

    /**
     * @return array{0: ?Subscription, 1: ?string} [абонемент или null, код отказа]
     *                 null при успехе;
     *                 «no_active» — нет по датам/статусу;
     *                 «wrong_club» — есть по датам, но не этот клуб;
     *                 «visits_exhausted» — срок/клуб ок, но посещения закончились.
     */
    public function resolveForEntry(User $user, ?Club $gateClub): array
    {
        $today = new \DateTimeImmutable('today');
        /** @var Subscription[] $subs */
        $subs = $this->em->getRepository(Subscription::class)->findBy(['user' => $user, 'status' => 'active']);

        $calendarOk = [];
        foreach ($subs as $sub) {
            // Подчищаем «зомби» с used >= total, которые ещё active.
            if ($this->lifecycle->cancelIfVisitsExhausted($sub)) {
                continue;
            }
            if ($sub->coversCalendarDay($today)) {
                $calendarOk[] = $sub;
            }
        }
        if ($calendarOk === []) {
            $this->em->flush();

            return [null, 'no_active'];
        }

        if ($gateClub === null) {
            $clubOk = $calendarOk;
        } else {
            $clubOk = [];
            foreach ($calendarOk as $sub) {
                if ($sub->isValidAtClub($gateClub)) {
                    $clubOk[] = $sub;
                }
            }
            if ($clubOk === []) {
                $this->em->flush();

                return [null, 'wrong_club'];
            }
        }

        foreach ($clubOk as $sub) {
            if ($sub->hasRemainingVisits()) {
                $this->em->flush();

                return [$sub, null];
            }
            $this->lifecycle->cancelIfVisitsExhausted($sub);
        }

        $this->em->flush();

        return [null, 'visits_exhausted'];
    }
}
