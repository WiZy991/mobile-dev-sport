<?php

namespace App\Service\Payment;

use App\Entity\Payment;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Принимает payment_token из приложения и создаёт платёж в ЮKassa.
 */
class YooKassaChargeService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly YooKassaClient $client,
        private readonly PaymentStatusSyncService $statusSync,
    ) {}

    /**
     * @return array{payment: Payment}|array{error: array<string, mixed>, status: int}
     */
    public function charge(Payment $payment, string $token): array
    {
        if ($payment->getGateway() !== Payment::GATEWAY_YOOKASSA) {
            return [
                'error' => [
                    'error' => 'Этот платёж проводится не через ЮKassa',
                    'code' => 'wrong_provider',
                ],
                'status' => 400,
            ];
        }

        if (!$payment->isPending() && !$payment->isPaid()) {
            return [
                'error' => [
                    'error' => $payment->getFailureReason() ?: 'Платёж уже завершён',
                    'code' => 'payment_closed',
                ],
                'status' => 409,
            ];
        }

        if ($payment->isPaid()) {
            return ['payment' => $payment];
        }

        if (!$this->client->isConfigured()) {
            return [
                'error' => [
                    'error' => 'ЮKassa не настроена на сервере',
                    'message' => 'Онлайн-оплата временно недоступна. Обратитесь к администратору клуба.',
                    'code' => 'yookassa_not_configured',
                ],
                'status' => 503,
            ];
        }

        if ($payment->getYookassaPaymentId() === null) {
            $created = $this->client->createPaymentWithToken($payment, $token);
            if (!$created->ok || $created->payment === null) {
                return [
                    'error' => [
                        'error' => $created->error ?? 'Не удалось создать платёж в ЮKassa',
                        'message' => $created->error ?? 'Не удалось создать платёж в ЮKassa',
                        'code' => $created->errorCode ?? 'yookassa_create_failed',
                    ],
                    'status' => $created->httpStatus >= 400 && $created->httpStatus < 500 ? 400 : 502,
                ];
            }

            $remoteId = isset($created->payment['id']) ? (string) $created->payment['id'] : '';
            if ($remoteId === '') {
                return [
                    'error' => [
                        'error' => 'ЮKassa не вернула идентификатор платежа',
                        'code' => 'yookassa_create_failed',
                    ],
                    'status' => 502,
                ];
            }

            $payment->setYookassaPaymentId($remoteId);
            $this->em->flush();
            $this->statusSync->applyRemoteState($payment, $created->payment);
        } else {
            $this->statusSync->syncFromGateway($payment);
        }

        $this->em->refresh($payment);

        return ['payment' => $payment];
    }
}
