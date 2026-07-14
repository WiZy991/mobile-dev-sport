<?php

declare(strict_types=1);

namespace App\Service\Admin;

use App\Entity\Payment;
use App\Entity\Sale;
use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use Doctrine\ORM\EntityManagerInterface;

final class SubscriptionPlanDeletionService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    public function countIssued(SubscriptionPlan $plan): int
    {
        return $this->em->getRepository(Subscription::class)->count(['plan' => $plan]);
    }

    public function countPayments(SubscriptionPlan $plan): int
    {
        return $this->em->getRepository(Payment::class)->count(['subscriptionPlan' => $plan]);
    }

    public function canDeleteWithoutForce(SubscriptionPlan $plan): bool
    {
        return $this->countIssued($plan) === 0 && $this->countPayments($plan) === 0;
    }

    /**
     * @return array{subscriptions: int, sales: int, payments: int}
     */
    public function delete(SubscriptionPlan $plan, bool $force = false): array
    {
        $issued = $this->countIssued($plan);
        $payments = $this->countPayments($plan);

        if (!$force && ($issued > 0 || $payments > 0)) {
            throw new \RuntimeException(sprintf(
                'Нельзя удалить тариф «%s»: выдано абонементов — %d, платежей — %d.',
                $plan->getName(),
                $issued,
                $payments,
            ));
        }

        $stats = ['subscriptions' => 0, 'sales' => 0, 'payments' => 0];

        $subscriptions = $this->em->getRepository(Subscription::class)->findBy(['plan' => $plan]);
        foreach ($subscriptions as $subscription) {
            foreach ($subscription->getSales()->toArray() as $sale) {
                $this->unlinkPaymentsFromSale($sale);
                $this->em->remove($sale);
                ++$stats['sales'];
            }

            foreach ($this->em->getRepository(Payment::class)->findBy(['subscription' => $subscription]) as $payment) {
                $payment->setSubscription(null);
            }

            $this->em->remove($subscription);
            ++$stats['subscriptions'];
        }

        foreach ($this->em->getRepository(Payment::class)->findBy(['subscriptionPlan' => $plan]) as $payment) {
            $sale = $payment->getSale();
            $payment->setSale(null);
            $this->em->remove($payment);
            ++$stats['payments'];

            if ($sale !== null && $this->em->contains($sale)) {
                $this->em->remove($sale);
                ++$stats['sales'];
            }
        }

        $this->em->remove($plan);
        $this->em->flush();

        return $stats;
    }

    private function unlinkPaymentsFromSale(Sale $sale): void
    {
        foreach ($this->em->getRepository(Payment::class)->findBy(['sale' => $sale]) as $payment) {
            $payment->setSale(null);
        }
    }
}
