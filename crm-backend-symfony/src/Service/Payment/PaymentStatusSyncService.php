<?php

namespace App\Service\Payment;

use App\Entity\Payment;
use Doctrine\ORM\EntityManagerInterface;

class PaymentStatusSyncService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AlfaAcquiringClient $alfaClient,
        private readonly PaymentFulfillmentService $fulfillmentService,
    ) {}

    /**
     * @param array<string, mixed>|null $callbackPayload
     */
    public function syncFromGateway(Payment $payment, ?array $callbackPayload = null): Payment
    {
        if ($callbackPayload !== null) {
            $payment->setRawCallback($callbackPayload);
        }

        if ($payment->isPaid()) {
            return $payment;
        }

        if ($payment->getAlfaOrderId() === null) {
            return $payment;
        }

        if ($payment->getExpiresAt() !== null && $payment->getExpiresAt() < new \DateTimeImmutable()) {
            $payment->setStatus(Payment::STATUS_EXPIRED);
            $this->em->flush();

            return $payment;
        }

        $status = $this->alfaClient->getOrderStatusExtended($payment->getAlfaOrderId());

        if ($status->isDeposited()) {
            if ($status->amountKopecks !== null && $status->amountKopecks !== $payment->getAmountKopecks()) {
                $payment->setStatus(Payment::STATUS_FAILED)
                    ->setFailureReason('Amount mismatch: expected ' . $payment->getAmountKopecks() . ', got ' . $status->amountKopecks);
                $this->em->flush();

                return $payment;
            }

            $this->fulfillmentService->fulfill($payment, $status->paymentWay);

            return $payment;
        }

        if ($status->isDeclined()) {
            $payment->setStatus(Payment::STATUS_FAILED)
                ->setFailureReason($status->errorMessage ?? 'Payment declined');
            $this->em->flush();
        }

        return $payment;
    }

    public function markFailed(Payment $payment, string $reason): Payment
    {
        if ($payment->isPaid()) {
            return $payment;
        }

        $payment->setStatus(Payment::STATUS_FAILED)
            ->setFailureReason($reason);
        $this->em->flush();

        return $payment;
    }
}
