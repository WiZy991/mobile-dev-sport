<?php

namespace App\Service\Payment;

use App\Entity\Club;
use App\Entity\Payment;
use App\Entity\Sale;
use App\Entity\Subscription;
use App\Service\Api\SubscriptionFreezePolicy;
use App\Service\Notification\ClientNotificationScheduler;
use Doctrine\ORM\EntityManagerInterface;

class PaymentFulfillmentService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionFreezePolicy $freezePolicy,
        private readonly ClientNotificationScheduler $notificationScheduler,
    ) {}

    public function fulfill(Payment $payment, ?string $paymentWay = null): Subscription
    {
        if ($payment->isPaid() && $payment->getSubscription() !== null) {
            return $payment->getSubscription();
        }

        if (!$payment->isPending() && !$payment->isPaid()) {
            throw new \RuntimeException('Payment cannot be fulfilled in status: ' . $payment->getStatus());
        }

        $user = $payment->getUser();
        $plan = $payment->getSubscriptionPlan();
        $promo = $payment->getPromoCode();
        $price = $payment->getAmountKopecks() / 100;

        $sub = new Subscription();
        $sub->setUser($user)
            ->setPlan($plan)
            ->setStatus('active')
            ->setVisitsUsed(0);

        $start = new \DateTimeImmutable();
        $sub->setStartDate($start);

        if ($plan->getDurationDays()) {
            $end = $start->modify('+' . $plan->getDurationDays() . ' days');
            $sub->setEndDate($end);
        }
        if ($plan->getVisitsCount()) {
            $sub->setVisitsTotal($plan->getVisitsCount());
        }
        $sub->setFreezeDaysTotal($this->freezePolicy->freezeDaysTotalForPlan($plan));
        $sub->setFreezeDaysUsed(0);
        if ($promo) {
            $sub->setPromoCode($promo);
        }

        $issueClub = $user->getClub();
        if ($issueClub === null) {
            $clubRepo = $this->em->getRepository(Club::class);
            if ((int) $clubRepo->count([]) === 1) {
                $issueClub = $clubRepo->findOneBy([]);
            }
        }
        $sub->setClub($issueClub);

        $this->em->persist($sub);
        $this->em->flush();

        $paymentMethod = $this->resolvePaymentMethod($paymentWay);

        $sale = (new Sale())
            ->setUser($user)
            ->setClientName($user->getName())
            ->setProductName('Абонемент: ' . $plan->getName())
            ->setQuantity(1)
            ->setPrice($price)
            ->setTotal($price)
            ->setPaymentMethod($paymentMethod)
            ->setSubscription($sub);
        if ($promo) {
            $sale->setPromoCode($promo);
            $sale->setDiscountAmount($payment->getDiscountAmount());
            $promo->incrementUsedCount();
        }

        $payment->setStatus(Payment::STATUS_PAID)
            ->setPaidAt(new \DateTimeImmutable())
            ->setPaymentWay($paymentWay)
            ->setSubscription($sub)
            ->setSale($sale);

        $this->em->persist($sale);
        $this->em->flush();

        $this->notificationScheduler->scheduleSubscriptionExpiryReminders($sub);

        return $sub;
    }

    private function resolvePaymentMethod(?string $paymentWay): string
    {
        if ($paymentWay === null) {
            return 'alfa_acquiring';
        }

        $upper = strtoupper($paymentWay);
        if (str_contains($upper, 'SBP')) {
            return 'alfa_sbp';
        }

        return 'alfa_acquiring';
    }
}
