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
        private readonly ?YooKassaClient $yooKassaClient = null,
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

        if ($payment->getGateway() === Payment::GATEWAY_YOOKASSA) {
            return $this->syncFromYooKassa($payment);
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

    /**
     * Применяет объект платежа ЮKassa (ответ create или GET).
     *
     * @param array<string, mixed> $remote
     */
    public function applyRemoteState(Payment $payment, array $remote, bool $allowCapture = true): Payment
    {
        $status = (string) ($remote['status'] ?? '');
        if ($status === 'waiting_for_capture' && $allowCapture && $this->yooKassaClient !== null) {
            $captured = $this->yooKassaClient->capture($payment);
            if ($captured->ok && $captured->payment !== null) {
                return $this->applyRemoteState($payment, $captured->payment, false);
            }
        }

        $this->rememberRemoteHints($payment, $remote);

        if ($status === 'succeeded') {
            return $this->markYooKassaPaid($payment, $remote);
        }
        if ($status === 'canceled') {
            return $this->markYooKassaCanceled($payment, $remote);
        }

        if (
            $payment->isPending()
            && $payment->getExpiresAt() !== null
            && $payment->getExpiresAt() < new \DateTimeImmutable()
            && $payment->getYookassaPaymentId() === null
        ) {
            $payment->setStatus(Payment::STATUS_EXPIRED);
        }

        $this->em->flush();

        return $payment;
    }

    private function syncFromYooKassa(Payment $payment): Payment
    {
        $remoteId = $payment->getYookassaPaymentId();
        if ($remoteId === null || $remoteId === '' || $this->yooKassaClient === null || !$this->yooKassaClient->isConfigured()) {
            if (
                $payment->isPending()
                && ($remoteId === null || $remoteId === '')
                && $payment->getExpiresAt() !== null
                && $payment->getExpiresAt() < new \DateTimeImmutable()
            ) {
                $payment->setStatus(Payment::STATUS_EXPIRED);
                $this->em->flush();
            }

            return $payment;
        }

        $remote = $this->yooKassaClient->getPayment($remoteId);
        if (!$remote->ok || $remote->payment === null) {
            return $payment;
        }

        return $this->applyRemoteState($payment, $remote->payment);
    }

    /**
     * @param array<string, mixed> $remote
     */
    private function rememberRemoteHints(Payment $payment, array $remote): void
    {
        $method = $remote['payment_method'] ?? null;
        if (is_array($method) && isset($method['type']) && is_string($method['type']) && $method['type'] !== '') {
            $payment->setPaymentWay($method['type']);
        }

        $confirmation = $remote['confirmation'] ?? null;
        if (is_array($confirmation)) {
            $url = $confirmation['confirmation_url'] ?? null;
            if (is_string($url) && $url !== '') {
                $payment->setPaymentUrl($url);
            }
        }
    }

    /**
     * @param array<string, mixed> $remote
     */
    private function markYooKassaPaid(Payment $payment, array $remote): Payment
    {
        $paymentId = $payment->getId();
        if ($paymentId === null) {
            return $payment;
        }

        $paidAmount = $this->remoteAmountKopecks($remote);
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
            if ($paidAmount !== null && $paidAmount > 0 && $paidAmount !== $locked->getAmountKopecks()) {
                $locked->setStatus(Payment::STATUS_FAILED)
                    ->setFailureReason('Amount mismatch: expected ' . $locked->getAmountKopecks() . ', got ' . $paidAmount);
                $this->em->flush();
                $this->em->commit();

                return $locked;
            }

            $way = $locked->getPaymentWay();
            $method = $remote['payment_method'] ?? null;
            if (is_array($method) && isset($method['type'])) {
                $way = (string) $method['type'];
            }
            $this->fulfillmentService->fulfill($locked, $way);
            $this->em->commit();

            return $locked;
        } catch (\Throwable $e) {
            if ($this->em->getConnection()->isTransactionActive()) {
                $this->em->rollback();
            }

            throw $e;
        }
    }

    /**
     * @param array<string, mixed> $remote
     */
    private function markYooKassaCanceled(Payment $payment, array $remote): Payment
    {
        if ($payment->isPaid()) {
            return $payment;
        }
        if ($payment->isPending()) {
            $payment->setStatus(Payment::STATUS_FAILED)
                ->setFailureReason($this->yooKassaFailureReason($remote));
            $this->em->flush();
        }

        return $payment;
    }

    /**
     * @param array<string, mixed> $remote
     */
    private function remoteAmountKopecks(array $remote): ?int
    {
        $amount = $remote['amount'] ?? null;
        if (!is_array($amount) || !isset($amount['value'])) {
            return null;
        }

        return (int) round(((float) $amount['value']) * 100);
    }

    /**
     * @param array<string, mixed> $remote
     */
    private function yooKassaFailureReason(array $remote): string
    {
        $details = $remote['cancellation_details'] ?? null;
        $reason = is_array($details) ? (string) ($details['reason'] ?? '') : '';

        return match ($reason) {
            'insufficient_funds' => 'Недостаточно средств',
            'card_expired' => 'Срок действия карты истёк',
            '3d_secure_failed' => 'Не пройдена проверка 3-D Secure',
            'payment_method_restricted' => 'Банк отклонил операцию',
            'expired_on_confirmation' => 'Время подтверждения оплаты истекло',
            'canceled_by_merchant', 'general_decline' => 'Оплата отклонена',
            default => $reason !== '' ? $reason : 'Оплата отменена',
        };
    }
}
