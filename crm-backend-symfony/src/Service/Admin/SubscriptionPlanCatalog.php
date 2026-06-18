<?php

declare(strict_types=1);

namespace App\Service\Admin;

use App\Entity\SubscriptionPlan;

/**
 * Стандартные тарифы (согласованы с приложением FitnessClub / LocalSubscriptionCatalog).
 *
 * @phpstan-type PlanTemplate array{
 *     name: string,
 *     description: string,
 *     price: float,
 *     duration_days: int|null,
 *     visits_count: int|null,
 *     type: string,
 *     is_popular: bool
 * }
 */
final class SubscriptionPlanCatalog
{
    /** @return array<string, PlanTemplate> */
    public function all(): array
    {
        return [
            'plan-12m' => [
                'name' => 'На 12 месяцев',
                'description' => 'Неограниченное посещение. Заморозка: +30 дней.',
                'price' => 38_000.0,
                'duration_days' => 365,
                'visits_count' => null,
                'type' => 'unlimited',
                'is_popular' => true,
            ],
            'plan-6m' => [
                'name' => 'На 6 месяцев',
                'description' => 'Неограниченное посещение. Заморозка: +20 дней.',
                'price' => 25_000.0,
                'duration_days' => 180,
                'visits_count' => null,
                'type' => 'unlimited',
                'is_popular' => false,
            ],
            'plan-3m' => [
                'name' => 'На 3 месяца',
                'description' => 'Неограниченное посещение. Заморозка: +14 дней.',
                'price' => 16_500.0,
                'duration_days' => 90,
                'visits_count' => null,
                'type' => 'unlimited',
                'is_popular' => false,
            ],
            'plan-4m' => [
                'name' => 'На 4 месяца',
                'description' => 'Неограниченное посещение. Заморозка: +14 дней.',
                'price' => 18_000.0,
                'duration_days' => 120,
                'visits_count' => null,
                'type' => 'unlimited',
                'is_popular' => false,
            ],
            'plan-1m' => [
                'name' => 'На 1 месяц',
                'description' => 'Неограниченное посещение.',
                'price' => 6_000.0,
                'duration_days' => 30,
                'visits_count' => null,
                'type' => 'unlimited',
                'is_popular' => false,
            ],
            'plan-single' => [
                'name' => 'Разовое посещение',
                'description' => 'Один визит в зал.',
                'price' => 990.0,
                'duration_days' => null,
                'visits_count' => 1,
                'type' => 'limited',
                'is_popular' => false,
            ],
        ];
    }

    /** @return PlanTemplate|null */
    public function find(string $key): ?array
    {
        return $this->all()[$key] ?? null;
    }

    public function applyTemplate(SubscriptionPlan $plan, string $key): void
    {
        $template = $this->find($key);
        if ($template === null) {
            throw new \InvalidArgumentException('Unknown plan template: ' . $key);
        }

        $plan
            ->setName($template['name'])
            ->setDescription($template['description'])
            ->setPrice($template['price'])
            ->setDurationDays($template['duration_days'])
            ->setVisitsCount($template['visits_count'])
            ->setType($template['type'])
            ->setIsPopular($template['is_popular']);
    }

    public function matchKeyForPlan(SubscriptionPlan $plan): ?string
    {
        foreach ($this->all() as $key => $template) {
            if ($template['name'] === $plan->getName()) {
                return $key;
            }
        }

        return null;
    }

    /**
     * @param list<SubscriptionPlan> $existingPlans
     * @return array<string, PlanTemplate>
     */
    public function availableFor(array $existingPlans): array
    {
        $existingNames = array_map(static fn (SubscriptionPlan $p) => $p->getName(), $existingPlans);

        return array_filter(
            $this->all(),
            static fn (array $template) => !in_array($template['name'], $existingNames, true),
        );
    }
}
