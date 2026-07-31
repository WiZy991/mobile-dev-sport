<?php

namespace App\Service\Payment;

use App\Entity\Payment;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
use App\Service\Api\SberMobileAuthService;
use Doctrine\ORM\EntityManagerInterface;

class SubscriptionPaymentInitService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionPurchaseQuoteService $quoteService,
        private readonly AlfaAcquiringClient $alfaClient,
        private readonly SberMobileAuthService $sberMobileAuth,
        private readonly bool $requireSberVerificationBeforePurchase,
        private readonly string $returnUrlBase,
        private readonly string $failUrlBase,
        private readonly string $callbackUrl,
        private readonly int $sessionTimeoutSecs,
        private readonly bool $fiscalizationEnabled,
        private readonly int $taxType,
    ) {}

    /**
     * @return array{payment: Payment}|array{error: array<string, mixed>, status: int}
     */
    public function init(User $user, SubscriptionPlan $plan, string $promoCodeRaw): array
    {
        $sberGate = $this->checkSberGate($user);
        if ($sberGate !== null) {
            return $sberGate;
        }

        $quote = $this->quoteService->quote($plan, $promoCodeRaw);

        if ($quote->amountKopecks <= 0) {
            return [
                'error' => ['error' => 'Сумма заказа должна быть больше нуля'],
                'status' => 400,
            ];
        }

        $reusable = $this->findReusablePendingPayment($user, $plan, $quote->amountKopecks);
        if ($reusable !== null) {
            return ['payment' => $reusable];
        }

        $payment = (new Payment())
            ->setUser($user)
            ->setType(Payment::TYPE_SUBSCRIPTION)
            ->setSubscriptionPlan($plan)
            ->setPromoCode($quote->promo)
            ->setAmountKopecks($quote->amountKopecks)
            ->setDiscountAmount($quote->discountAmount)
            ->setStatus(Payment::STATUS_PENDING)
            ->setExpiresAt((new \DateTimeImmutable())->modify('+' . $this->sessionTimeoutSecs . ' seconds'));

        $this->em->persist($payment);
        $this->em->flush();

        $orderNumber = sprintf('sub-%d-%s', $payment->getId(), bin2hex(random_bytes(4)));
        $payment->setOrderNumber($orderNumber);
        $this->em->flush();

        $returnUrl = $this->appendPaymentId($this->returnUrlBase, $payment->getId());
        $failUrl = $this->appendPaymentId($this->failUrlBase, $payment->getId(), 'fail');

        $orderBundle = null;
        if ($this->fiscalizationEnabled) {
            $orderBundle = $this->buildOrderBundle($plan, $quote);
        }

        $registerResponse = $this->alfaClient->registerOrder(new AlfaRegisterOrderRequest(
            orderNumber: $orderNumber,
            amountKopecks: $quote->amountKopecks,
            returnUrl: $returnUrl,
            failUrl: $failUrl,
            description: 'Абонемент: ' . $plan->getName(),
            dynamicCallbackUrl: $this->callbackUrl !== '' ? $this->callbackUrl : null,
            email: $user->getEmail(),
            phone: $this->normalizePhone($user->getPhone()),
            orderBundle: $orderBundle,
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

    /**
     * Повторный «Купить» без оплаты — вернуть тот же pending, не плодить заказы в Альфе.
     */
    private function findReusablePendingPayment(User $user, SubscriptionPlan $plan, int $amountKopecks): ?Payment
    {
        /** @var Payment|null $existing */
        $existing = $this->em->createQueryBuilder()
            ->select('p')
            ->from(Payment::class, 'p')
            ->where('p.user = :user')
            ->andWhere('p.subscriptionPlan = :plan')
            ->andWhere('p.type = :type')
            ->andWhere('p.status = :pending')
            ->andWhere('p.amountKopecks = :amount')
            ->andWhere('p.paymentUrl IS NOT NULL')
            ->andWhere('p.expiresAt IS NULL OR p.expiresAt > :now')
            ->setParameter('user', $user)
            ->setParameter('plan', $plan)
            ->setParameter('type', Payment::TYPE_SUBSCRIPTION)
            ->setParameter('pending', Payment::STATUS_PENDING)
            ->setParameter('amount', $amountKopecks)
            ->setParameter('now', new \DateTimeImmutable())
            ->orderBy('p.id', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return $existing;
    }

    /**
     * @return array{error: array<string, mixed>, status: int}|null
     */
    private function checkSberGate(User $user): ?array
    {
        if (!$this->requireSberVerificationBeforePurchase) {
            return null;
        }

        if (!$this->sberMobileAuth->isReady()) {
            return [
                'error' => [
                    'code' => 'sber_not_configured',
                    'error' => 'Включена обязательная верификация, но Сбер ID для приложения не настроен (SBER_ID_*, SBER_ID_MOBILE_REDIRECT_URI).',
                ],
                'status' => 503,
            ];
        }

        if ($user->getPassportVerificationStatus() !== 'verified') {
            try {
                $authorizeUrl = $this->sberMobileAuth->buildAuthorizeUrlForUser($user);
            } catch (\Throwable $e) {
                return [
                    'error' => [
                        'code' => 'sber_url_failed',
                        'error' => $e->getMessage(),
                    ],
                    'status' => 500,
                ];
            }

            return [
                'error' => [
                    'code' => 'verification_required',
                    'message' => 'Требуется верификация через Сбер ID. После успеха вернитесь в приложение и нажмите «Купить» снова.',
                    'authorize_url' => $authorizeUrl,
                ],
                'status' => 403,
            ];
        }

        return null;
    }

    private function appendPaymentId(string $baseUrl, int $paymentId, string $result = 'success'): string
    {
        $separator = str_contains($baseUrl, '?') ? '&' : '?';

        return $baseUrl . $separator . http_build_query([
            'payment_id' => $paymentId,
            'result' => $result,
        ]);
    }

    private function normalizePhone(string $phone): ?string
    {
        $digits = preg_replace('/\D+/', '', $phone);
        if ($digits === null || $digits === '') {
            return null;
        }
        if (str_starts_with($digits, '8') && strlen($digits) === 11) {
            return '+7' . substr($digits, 1);
        }
        if (str_starts_with($digits, '7') && strlen($digits) === 11) {
            return '+' . $digits;
        }

        return $phone;
    }

    private function buildOrderBundle(SubscriptionPlan $plan, SubscriptionPurchaseQuote $quote): array
    {
        return [
            'cartItems' => [
                'items' => [[
                    'positionId' => 1,
                    'name' => 'Абонемент: ' . $plan->getName(),
                    'quantity' => ['value' => '1', 'measure' => 'шт'],
                    'itemAmount' => $quote->amountKopecks,
                    'itemPrice' => $quote->amountKopecks,
                    'tax' => ['taxType' => $this->taxType],
                    'itemAttributes' => [
                        'attributes' => [
                            ['name' => 'paymentMethod', 'value' => '1'],
                            ['name' => 'paymentObject', 'value' => '4'],
                        ],
                    ],
                ]],
            ],
        ];
    }
}
