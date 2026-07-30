<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use Doctrine\ORM\EntityManagerInterface;

final class SubscriptionLifecycleService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function canExtend(Subscription $subscription): bool
    {
        return \in_array($subscription->getStatus(), ['active', 'frozen'], true);
    }

    public function canCancel(Subscription $subscription): bool
    {
        if (!\in_array($subscription->getStatus(), ['active', 'frozen'], true)) {
            return false;
        }
        // Исчерпанный по посещениям абонемент отменяется автоматически — ручная отмена не нужна.
        if ($subscription->getVisitsTotal() !== null && !$subscription->hasRemainingVisits()) {
            return false;
        }

        return true;
    }

    public function canChangePlan(Subscription $subscription): bool
    {
        return \in_array($subscription->getStatus(), ['active', 'frozen'], true);
    }

    /** @return string|null Сообщение об ошибке или null при успехе */
    public function extend(Subscription $subscription, int $days): ?string
    {
        if ($days <= 0) {
            return 'Укажите количество дней';
        }
        if (!$this->canExtend($subscription)) {
            return 'Продлить можно только активный или замороженный абонемент';
        }

        $base = $subscription->getEndDate() ?? new \DateTimeImmutable('today');
        $subscription->setEndDate($base->modify('+' . $days . ' days'));

        return null;
    }

    /** @return string|null Сообщение об ошибке или null при успехе */
    public function cancel(Subscription $subscription): ?string
    {
        if (!$this->canCancel($subscription)) {
            if ($subscription->getVisitsTotal() !== null && !$subscription->hasRemainingVisits()) {
                $this->cancelIfVisitsExhausted($subscription);

                return 'Посещения закончились — абонемент уже отменён';
            }

            return 'Абонемент уже отменён или истёк';
        }

        $subscription->setStatus('cancelled');

        return null;
    }

    /**
     * Если лимит посещений исчерпан (used >= total) — отменяет абонемент и обрезает used до total.
     *
     * @return bool true, если статус стал cancelled из‑за лимита
     */
    public function cancelIfVisitsExhausted(Subscription $subscription): bool
    {
        if (!\in_array($subscription->getStatus(), ['active', 'frozen'], true)) {
            return false;
        }
        $total = $subscription->getVisitsTotal();
        if ($total === null) {
            return false;
        }
        $used = (int) ($subscription->getVisitsUsed() ?? 0);
        if ($used < $total) {
            return false;
        }
        if ($used > $total) {
            $subscription->setVisitsUsed($total);
        }
        $subscription->setStatus('cancelled');

        return true;
    }

    /**
     * Атомарно списывает одно посещение при входе.
     * Безлимит (visitsTotal = null) — true без изменений.
     * Если лимит уже исчерпан или гонка — false (вход запрещён).
     * После последнего посещения абонемент отменяется.
     */
    public function consumeVisitForEntry(Subscription $subscription): bool
    {
        if ($subscription->getVisitsTotal() === null) {
            return true;
        }

        $id = $subscription->getId();
        if ($id === null) {
            return false;
        }

        $affected = $this->em->getConnection()->executeStatement(
            'UPDATE subscriptions
             SET visits_used = COALESCE(visits_used, 0) + 1
             WHERE id = :id
               AND status = \'active\'
               AND visits_total IS NOT NULL
               AND COALESCE(visits_used, 0) < visits_total',
            ['id' => $id],
        );

        if ($affected < 1) {
            $this->em->refresh($subscription);
            $this->cancelIfVisitsExhausted($subscription);

            return false;
        }

        $this->em->refresh($subscription);
        $this->cancelIfVisitsExhausted($subscription);

        return true;
    }

    /**
     * Меняет тариф, даты и visitsUsed не трогает; visitsTotal берётся из нового тарифа.
     *
     * @return string|null Сообщение об ошибке или null при успехе
     */
    public function changePlan(Subscription $subscription, SubscriptionPlan $plan): ?string
    {
        if (!$this->canChangePlan($subscription)) {
            return 'Сменить тариф можно только у активного или замороженного абонемента';
        }
        if ($subscription->getPlan()->getId() === $plan->getId()) {
            return 'Выбран тот же тариф';
        }

        $subscription->setPlan($plan);
        $subscription->setVisitsTotal($plan->getVisitsCount());

        return null;
    }
}
