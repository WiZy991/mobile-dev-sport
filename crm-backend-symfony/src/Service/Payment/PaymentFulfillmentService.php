<?php

namespace App\Service\Payment;

use App\Entity\Club;
use App\Entity\Payment;
use App\Entity\Sale;
use App\Entity\Subscription;
use App\Service\Api\SubscriptionFreezePolicy;
use App\Service\Notification\ClientEmailNotifier;
use App\Service\Notification\ClientNotificationScheduler;
use App\Service\Notification\ClientNotificationService;
use App\Service\Staff\StaffClubRentalService;
use Doctrine\ORM\EntityManagerInterface;

class PaymentFulfillmentService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionFreezePolicy $freezePolicy,
        private readonly ClientNotificationScheduler $notificationScheduler,
        private readonly ClientNotificationService $clientNotifications,
        private readonly ClientEmailNotifier $emailNotifier,
        private readonly StaffClubRentalService $clubRentals,
    ) {}

    public function fulfill(Payment $payment, ?string $paymentWay = null): ?Subscription
    {
        if ($payment->getType() === Payment::TYPE_TRAINER_RENTAL) {
            $this->fulfillTrainerRental($payment, $paymentWay);

            return null;
        }

        if ($payment->isPaid() && $payment->getSubscription() !== null) {
            return $payment->getSubscription();
        }

        // Уже оплачен / не pending — второй параллельный вызов не должен создать ещё один Sale.
        if (!$payment->isPending()) {
            if ($payment->isPaid()) {
                return $payment->getSubscription();
            }
            throw new \RuntimeException('Payment cannot be fulfilled in status: ' . $payment->getStatus());
        }

        $user = $payment->getUser();
        $plan = $payment->getSubscriptionPlan();
        if ($user === null || $plan === null) {
            throw new \RuntimeException('Subscription payment missing user or plan');
        }
        $promo = $payment->getPromoCode();
        $price = $payment->getAmountKopecks() / 100;

        // Сразу помечаем paid до создания Sale — второй поток (после lock/refresh) увидит не-pending.
        $payment->setStatus(Payment::STATUS_PAID)
            ->setPaidAt(new \DateTimeImmutable())
            ->setPaymentWay($paymentWay);
        $this->em->flush();

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

        $payment->setSubscription($sub)->setSale($sale);

        $this->em->persist($sale);
        $this->em->flush();

        $this->expireSiblingPendingPayments($payment);
        $this->notificationScheduler->scheduleSubscriptionExpiryReminders($sub);
        $this->sendSubscriptionPaidConfirmation($payment, $sub);

        return $sub;
    }

    /**
     * Письмо-подтверждение после оплаты (пункт 8 репорта): клиент должен получить
     * касание от нас, а не только чек банка.
     */
    private function sendSubscriptionPaidConfirmation(Payment $payment, Subscription $sub): void
    {
        $user = $payment->getUser();
        $plan = $payment->getSubscriptionPlan();
        if ($user === null || $plan === null) {
            return;
        }

        $lines = ['Оплата прошла успешно, абонемент «' . $plan->getName() . '» активирован.'];
        $start = $sub->getStartDate();
        $end = $sub->getEndDate();
        if ($start !== null && $end !== null) {
            $lines[] = 'Срок действия: с ' . $start->format('d.m.Y') . ' по ' . $end->format('d.m.Y') . '.';
        } elseif ($end !== null) {
            $lines[] = 'Действует до ' . $end->format('d.m.Y') . '.';
        }
        if ($sub->getVisitsTotal() !== null && $sub->getVisitsTotal() > 0) {
            $lines[] = 'Посещений по абонементу: ' . $sub->getVisitsTotal() . '.';
        }
        $lines[] = 'Сумма: ' . number_format($payment->getAmountKopecks() / 100, 2, ',', ' ') . ' ₽.';
        $lines[] = '';
        $lines[] = 'Абонемент уже доступен в приложении. Если возникнут вопросы — просто ответьте на это письмо или создайте обращение в приложении.';

        try {
            $this->clientNotifications->notify(
                $user,
                'payment',
                'Оплата прошла успешно',
                implode("\n", $lines),
                $payment->getId() !== null ? 'payment-' . $payment->getId() : null,
                force: true,
                forceEmail: true,
            );
        } catch (\Throwable) {
            // Подтверждение — вторичное действие; оно не должно ломать зачисление оплаты.
        }
    }

    private function fulfillTrainerRental(Payment $payment, ?string $paymentWay): void
    {
        if ($payment->isPaid()) {
            return;
        }
        if (!$payment->isPending()) {
            throw new \RuntimeException('Payment cannot be fulfilled in status: ' . $payment->getStatus());
        }

        $payment->setStatus(Payment::STATUS_PAID)
            ->setPaidAt(new \DateTimeImmutable())
            ->setPaymentWay($paymentWay);
        $this->em->flush();

        $staff = $payment->getStaffUser();
        if ($staff === null) {
            throw new \RuntimeException('Trainer rental payment missing staff user');
        }

        $club = $payment->getClub();
        if ($club === null) {
            // Legacy-платежи без club_id: первый клуб из каталога.
            $catalog = $this->clubRentals->catalogClubs($staff);
            $club = $catalog[0] ?? null;
        }
        if ($club === null) {
            throw new \RuntimeException('Trainer rental payment missing club');
        }

        $rental = $this->clubRentals->extendRental($staff, $club, StaffClubRentalService::RENTAL_DAYS);
        $paidUntil = $rental->getPaidUntil();

        $clubLabel = trim($club->getName());
        $price = $payment->getAmountKopecks() / 100;
        $sale = (new Sale())
            ->setUser(null)
            ->setClientName($staff->getName() !== '' ? $staff->getName() : $staff->getEmail())
            ->setProductName(sprintf(
                'Аренда клуба (тренер) — %d дн.%s',
                StaffClubRentalService::RENTAL_DAYS,
                $clubLabel !== '' ? ' — ' . $clubLabel : '',
            ))
            ->setQuantity(1)
            ->setPrice($price)
            ->setTotal($price)
            ->setPaymentMethod($this->resolvePaymentMethod($paymentWay));
        if ($staff->getOrganization() !== null) {
            $sale->setOrganization($staff->getOrganization());
        }

        $payment->setSale($sale);

        $this->em->persist($sale);
        $this->em->flush();

        $staffEmail = $staff->getEmail();
        if ($staffEmail !== '') {
            try {
                $this->emailNotifier->send(
                    $staffEmail,
                    'Оплата аренды прошла успешно',
                    implode("\n", array_filter([
                        'Оплата аренды клуба прошла успешно.',
                        $clubLabel !== '' ? 'Зал: ' . $clubLabel . '.' : null,
                        'Сумма: ' . number_format($payment->getAmountKopecks() / 100, 2, ',', ' ') . ' ₽.',
                        'Доступ активен до ' . $paidUntil->format('d.m.Y') . '.',
                        '',
                        'Приложение для тренеров уже доступно. Если возникнут вопросы — ответьте на это письмо.',
                    ])),
                );
            } catch (\Throwable) {
            }
        }
    }

    private function expireSiblingPendingPayments(Payment $paid): void
    {
        $user = $paid->getUser();
        $plan = $paid->getSubscriptionPlan();
        $paidId = $paid->getId();
        if ($user === null || $plan === null || $paidId === null) {
            return;
        }

        /** @var list<Payment> $siblings */
        $siblings = $this->em->createQueryBuilder()
            ->select('p')
            ->from(Payment::class, 'p')
            ->where('p.user = :user')
            ->andWhere('p.subscriptionPlan = :plan')
            ->andWhere('p.type = :type')
            ->andWhere('p.status = :pending')
            ->andWhere('p.id != :id')
            ->setParameter('user', $user)
            ->setParameter('plan', $plan)
            ->setParameter('type', Payment::TYPE_SUBSCRIPTION)
            ->setParameter('pending', Payment::STATUS_PENDING)
            ->setParameter('id', $paidId)
            ->getQuery()
            ->getResult();

        foreach ($siblings as $sibling) {
            $sibling->setStatus(Payment::STATUS_EXPIRED)
                ->setFailureReason('Superseded by payment #' . $paidId);
        }
        if ($siblings !== []) {
            $this->em->flush();
        }
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
