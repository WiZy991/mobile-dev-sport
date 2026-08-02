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
        public readonly ?float $groupDiscountPercent = null,
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

    /**
     * Цена с учётом скидки группы клиента, затем промокода.
     */
    public function quote(
        SubscriptionPlan $plan,
        string $promoCodeRaw,
        bool $reservePromo = false,
        ?User $user = null,
    ): SubscriptionPurchaseQuote {
        $listPrice = $plan->getPrice();
        $price = $listPrice;
        $discountAmount = 0.0;
        $promo = null;
        $groupDiscountPercent = null;

        if ($user !== null) {
            $group = $user->getClientGroup();
            if ($group !== null && $group->getDiscountPercent() > 0) {
                $groupDiscountPercent = $group->getDiscountPercent();
                $groupDisc = round($listPrice * $groupDiscountPercent / 100, 2);
                $discountAmount += $groupDisc;
                $price = max(0.0, $listPrice - $groupDisc);
            }
        }

        if ($promoCodeRaw !== '') {
            $promo = $this->em->getRepository(PromoCode::class)->findOneBy([
                'code' => strtoupper($promoCodeRaw),
            ]);
            if ($promo && $promo->isValid()) {
                $promoDisc = 0.0;
                if ($promo->getDiscountPercent() !== null) {
                    $promoDisc = round($price * $promo->getDiscountPercent() / 100, 2);
                } elseif ($promo->getDiscountAmount() !== null) {
                    $promoDisc = min($promo->getDiscountAmount(), $price);
                }
                $discountAmount += $promoDisc;
                $price = max(0.0, $price - $promoDisc);
            } else {
                $promo = null;
            }
        }

        if ($reservePromo && $promo !== null) {
            // Promo usage is incremented only after successful payment in fulfillment.
        }

        return new SubscriptionPurchaseQuote(
            originalPrice: $listPrice,
            finalPrice: $price,
            discountAmount: $discountAmount,
            amountKopecks: (int) round($price * 100),
            promo: $promo,
            groupDiscountPercent: $groupDiscountPercent,
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
