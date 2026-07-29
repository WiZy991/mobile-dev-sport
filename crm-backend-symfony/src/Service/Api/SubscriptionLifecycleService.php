<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;

final class SubscriptionLifecycleService
{
    public function canExtend(Subscription $subscription): bool
    {
        return \in_array($subscription->getStatus(), ['active', 'frozen'], true);
    }

    public function canCancel(Subscription $subscription): bool
    {
        return \in_array($subscription->getStatus(), ['active', 'frozen'], true);
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
            return 'Абонемент уже отменён или истёк';
        }

        $subscription->setStatus('cancelled');

        return null;
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
