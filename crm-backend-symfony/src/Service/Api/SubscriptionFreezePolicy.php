<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\SubscriptionPlan;

/**
 * Лимиты заморозки по сроку абонемента:
 * 1 мес — 0, 3 мес — 14, 6 мес — 20, 12 мес — 30 дней.
 */
final class SubscriptionFreezePolicy
{
    public function freezeDaysTotalForPlan(SubscriptionPlan $plan): int
    {
        if ($plan->getType() === 'personal') {
            return 0;
        }

        $duration = $plan->getDurationDays();
        if ($duration === null || $duration <= 31) {
            return 0;
        }
        if ($duration <= 120) {
            return 14;
        }
        if ($duration <= 200) {
            return 20;
        }

        return 30;
    }
}
