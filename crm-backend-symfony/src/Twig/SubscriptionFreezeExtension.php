<?php

declare(strict_types=1);

namespace App\Twig;

use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Service\Api\SubscriptionFreezeService;
use App\Service\Api\SubscriptionLifecycleService;
use Twig\Extension\AbstractExtension;
use Twig\TwigFunction;

final class SubscriptionFreezeExtension extends AbstractExtension
{
    public function __construct(
        private readonly SubscriptionFreezeService $freezeService,
        private readonly SubscriptionLifecycleService $lifecycleService,
    ) {
    }

    public function getFunctions(): array
    {
        return [
            new TwigFunction('subscription_freeze_days_for_plan', $this->freezeDaysForPlan(...)),
            new TwigFunction('subscription_freeze_days_left', $this->freezeDaysLeft(...)),
            new TwigFunction('subscription_can_freeze', $this->canFreeze(...)),
            new TwigFunction('subscription_can_extend', $this->canExtend(...)),
            new TwigFunction('subscription_can_cancel', $this->canCancel(...)),
        ];
    }

    public function freezeDaysForPlan(SubscriptionPlan $plan): int
    {
        return $this->freezeService->freezeDaysTotalForPlan($plan);
    }

    public function freezeDaysLeft(Subscription $subscription): int
    {
        return $this->freezeService->freezeDaysLeft($subscription);
    }

    public function canFreeze(Subscription $subscription): bool
    {
        return $this->freezeService->canFreeze($subscription);
    }

    public function canExtend(Subscription $subscription): bool
    {
        return $this->lifecycleService->canExtend($subscription);
    }

    public function canCancel(Subscription $subscription): bool
    {
        return $this->lifecycleService->canCancel($subscription);
    }
}
