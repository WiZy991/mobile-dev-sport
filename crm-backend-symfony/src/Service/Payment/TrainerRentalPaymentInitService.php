<?php

declare(strict_types=1);

namespace App\Service\Payment;

use App\Entity\Payment;
use App\Entity\StaffUser;
use App\Service\Staff\StaffOnboardingService;
use Doctrine\ORM\EntityManagerInterface;

final class TrainerRentalPaymentInitService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly StaffOnboardingService $onboarding,
        private readonly AlfaAcquiringClient $alfaClient,
        private readonly string $returnUrlBase,
        private readonly string $failUrlBase,
        private readonly string $callbackUrl,
        private readonly int $sessionTimeoutSecs,
        private readonly string $staffAppBridgeUri = 'staffapp://payment/callback',
    ) {
    }

    /**
     * @return array{payment: Payment}|array{error: array<string, mixed>, status: int}
     */
    public function init(StaffUser $staff, bool $offerAccepted): array
    {
        if ($staff->getRegistrationStatus() !== StaffUser::REGISTRATION_APPROVED) {
            return [
                'error' => ['error' => 'Регистрация ещё не одобрена', 'code' => 'not_approved'],
                'status' => 403,
            ];
        }
        if (!$staff->requiresTrainerRental()) {
            return [
                'error' => ['error' => 'Аренда для этой учётной записи не требуется', 'code' => 'rental_not_required'],
                'status' => 400,
            ];
        }
        if (!$offerAccepted) {
            return [
                'error' => ['error' => 'Необходимо принять публичную оферту', 'code' => 'offer_required'],
                'status' => 400,
            ];
        }

        $amount = $this->onboarding->rentalAmountKopecks();
        if ($amount <= 0) {
            return [
                'error' => ['error' => 'Сумма аренды не настроена в CRM', 'code' => 'rental_amount_missing'],
                'status' => 400,
            ];
        }

        $staff->setOfferAcceptedAt(new \DateTimeImmutable());

        $payment = (new Payment())
            ->setStaffUser($staff)
            ->setUser(null)
            ->setType(Payment::TYPE_TRAINER_RENTAL)
            ->setSubscriptionPlan(null)
            ->setAmountKopecks($amount)
            ->setDiscountAmount(0)
            ->setStatus(Payment::STATUS_PENDING)
            ->setExpiresAt((new \DateTimeImmutable())->modify('+' . $this->sessionTimeoutSecs . ' seconds'));

        $this->em->persist($payment);
        $this->em->flush();

        $orderNumber = sprintf('rent-%d-%s', $payment->getId(), bin2hex(random_bytes(4)));
        $payment->setOrderNumber($orderNumber);
        $this->em->flush();

        $returnUrl = $this->appendPaymentId($this->returnUrlBase, $payment->getId());
        $failUrl = $this->appendPaymentId($this->failUrlBase, $payment->getId(), 'fail');

        $registerResponse = $this->alfaClient->registerOrder(new AlfaRegisterOrderRequest(
            orderNumber: $orderNumber,
            amountKopecks: $amount,
            returnUrl: $returnUrl,
            failUrl: $failUrl,
            description: 'Аренда клуба (тренер) — 1 месяц',
            dynamicCallbackUrl: $this->callbackUrl !== '' ? $this->callbackUrl : null,
            email: $staff->getEmail(),
            phone: null,
            orderBundle: null,
            sessionTimeoutSecs: $this->sessionTimeoutSecs,
        ));

        if (!$registerResponse->isSuccess()) {
            $payment->setStatus(Payment::STATUS_FAILED)
                ->setFailureReason($registerResponse->errorMessage ?? 'register.do failed');
            $this->em->flush();

            return [
                'error' => [
                    'error' => $registerResponse->errorMessage ?? 'Не удалось зарегистрировать платёж',
                    'code' => 'alfa_register_failed',
                    'alfa_error_code' => $registerResponse->errorCode,
                ],
                'status' => 502,
            ];
        }

        $payment->setAlfaOrderId($registerResponse->orderId)
            ->setPaymentUrl($registerResponse->formUrl);
        $this->em->flush();

        return ['payment' => $payment];
    }

    public function staffBridgeUri(): string
    {
        return $this->staffAppBridgeUri;
    }

    private function appendPaymentId(string $baseUrl, int $paymentId, string $result = 'success'): string
    {
        $separator = str_contains($baseUrl, '?') ? '&' : '?';

        return $baseUrl . $separator . http_build_query([
            'payment_id' => $paymentId,
            'result' => $result,
        ]);
    }
}
