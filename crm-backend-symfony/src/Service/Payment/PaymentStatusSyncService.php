<?php

namespace App\Service\Payment;

use App\Entity\Payment;
use Doctrine\DBAL\LockMode;
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
        $paymentId = $payment->getId();
        if ($paymentId === null) {
            return $payment;
        }

        if ($callbackPayload !== null) {
            $payment->setRawCallback($callbackPayload);
            $this->em->flush();
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

        // HTTP к шлюзу — вне блокировки; гонка callback+poll закрывается lock ниже.
        $status = $this->alfaClient->getOrderStatusExtended($payment->getAlfaOrderId());

        if (!$status->isDeposited() && !$status->isDeclined()) {
            return $payment;
        }

        $this->em->beginTransaction();
        try {
            /** @var Payment|null $locked */
            $locked = $this->em->find(Payment::class, $paymentId, LockMode::PESSIMISTIC_WRITE);
            if ($locked === null) {
                $this->em->rollback();

                return $payment;
            }

            if ($locked->isPaid()) {
                $this->em->commit();

                return $locked;
            }

            if ($status->isDeposited()) {
                $paidAmount = $status->amountKopecks;
                if ($paidAmount !== null && $paidAmount > 0 && $paidAmount !== $locked->getAmountKopecks()) {
                    $locked->setStatus(Payment::STATUS_FAILED)
                        ->setFailureReason('Amount mismatch: expected ' . $locked->getAmountKopecks() . ', got ' . $status->amountKopecks);
                    $this->em->flush();
                    $this->em->commit();

                    return $locked;
                }

                $this->fulfillmentService->fulfill($locked, $status->paymentWay);
                $this->em->commit();

                return $locked;
            }

            // declined
            if ($locked->isPending()) {
                $locked->setStatus(Payment::STATUS_FAILED)
                    ->setFailureReason($status->errorMessage ?? 'Payment declined');
                $this->em->flush();
            }
            $this->em->commit();

            return $locked;
        } catch (\Throwable $e) {
            if ($this->em->getConnection()->isTransactionActive()) {
                $this->em->rollback();
            }

            throw $e;
        }
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
