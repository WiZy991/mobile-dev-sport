<?php

namespace App\Service\Payment;

use App\Entity\PromoCode;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class SubscriptionPurchaseQuote
{
    public function __construct(
        public readonly float $originalPrice,
        public readonly float $finalPrice,
        public readonly float $discountAmount,
        public readonly int $amountKopecks,
        public readonly ?PromoCode $promo,
    ) {}
}

class SubscriptionPurchaseQuoteService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    public function resolvePlanId(string $planIdRaw): ?int
    {
        if (str_starts_with($planIdRaw, 'plan-')) {
            return (int) substr($planIdRaw, 5);
        }

        return (int) $planIdRaw ?: null;
    }

    public function findPlan(string $planIdRaw): ?SubscriptionPlan
    {
        $planId = $this->resolvePlanId($planIdRaw);
        if ($planId === null || $planId <= 0) {
            return null;
        }

        return $this->em->getRepository(SubscriptionPlan::class)->find($planId);
    }

    public function quote(SubscriptionPlan $plan, string $promoCodeRaw, bool $reservePromo = false): SubscriptionPurchaseQuote
    {
        $price = $plan->getPrice();
        $discountAmount = 0.0;
        $promo = null;

        if ($promoCodeRaw !== '') {
            $promo = $this->em->getRepository(PromoCode::class)->findOneBy([
                'code' => strtoupper($promoCodeRaw),
            ]);
            if ($promo && $promo->isValid()) {
                if ($promo->getDiscountPercent() !== null) {
                    $discountAmount = round($price * $promo->getDiscountPercent() / 100, 2);
                } elseif ($promo->getDiscountAmount() !== null) {
                    $discountAmount = min($promo->getDiscountAmount(), $price);
                }
                $price = max(0, $price - $discountAmount);
            } else {
                $promo = null;
                $discountAmount = 0.0;
            }
        }

        if ($reservePromo && $promo !== null) {
            // Promo usage is incremented only after successful payment in fulfillment.
        }

        return new SubscriptionPurchaseQuote(
            originalPrice: $plan->getPrice(),
            finalPrice: $price,
            discountAmount: $discountAmount,
            amountKopecks: (int) round($price * 100),
            promo: $promo,
        );
    }

    public function validatePromoOnly(string $promoCodeRaw): bool
    {
        if ($promoCodeRaw === '') {
            return false;
        }

        $promo = $this->em->getRepository(PromoCode::class)->findOneBy([
            'code' => strtoupper($promoCodeRaw),
        ]);

        return $promo !== null && $promo->isValid();
    }
}
