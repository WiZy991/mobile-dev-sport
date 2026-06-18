<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;

final class SubscriptionFreezeService
{
    public function __construct(
        private readonly SubscriptionFreezePolicy $policy,
    ) {
    }

    public function freezeDaysTotalForPlan(SubscriptionPlan $plan): int
    {
        return $this->policy->freezeDaysTotalForPlan($plan);
    }

    public function freezeDaysLeft(Subscription $subscription): int
    {
        $total = $subscription->getFreezeDaysTotal() ?? 0;
        $used = $subscription->getFreezeDaysUsed() ?? 0;

        return max(0, $total - $used);
    }

    public function canFreeze(Subscription $subscription): bool
    {
        return $subscription->getStatus() === 'active'
            && ($subscription->getFreezeDaysTotal() ?? 0) > 0
            && $this->freezeDaysLeft($subscription) > 0;
    }

    /** @return string|null Сообщение об ошибке или null при успехе */
    public function freeze(Subscription $subscription, int $days): ?string
    {
        if ($days <= 0) {
            return 'Укажите количество дней';
        }

        $total = $subscription->getFreezeDaysTotal() ?? 0;
        if ($total <= 0) {
            return 'Заморозка недоступна для этого абонемента';
        }
        if ($subscription->getStatus() !== 'active') {
            return 'Абонемент уже заморожен или не активен';
        }

        $left = $this->freezeDaysLeft($subscription);
        if ($days > $left) {
            return 'Недостаточно дней заморозки. Осталось: ' . $left;
        }

        if ($subscription->getEndDate() !== null) {
            $subscription->setEndDate($subscription->getEndDate()->modify('+' . $days . ' days'));
        }

        $subscription->setFreezeDaysUsed(($subscription->getFreezeDaysUsed() ?? 0) + $days);
        $subscription->setStatus('frozen');

        return null;
    }

    /** @return string|null Сообщение об ошибке или null при успехе */
    public function unfreeze(Subscription $subscription): ?string
    {
        if ($subscription->getStatus() !== 'frozen') {
            return 'Абонемент не заморожен';
        }

        $subscription->setStatus('active');

        return null;
    }
}
