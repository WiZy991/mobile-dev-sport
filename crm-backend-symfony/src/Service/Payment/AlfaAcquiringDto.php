<?php

namespace App\Service\Payment;

final class AlfaRegisterOrderRequest
{
    public function __construct(
        public readonly string $orderNumber,
        public readonly int $amountKopecks,
        public readonly string $returnUrl,
        public readonly string $failUrl,
        public readonly string $description,
        public readonly ?string $dynamicCallbackUrl = null,
        public readonly ?string $email = null,
        public readonly ?string $phone = null,
        public readonly ?array $orderBundle = null,
        public readonly int $sessionTimeoutSecs = 1200,
    ) {}
}

final class AlfaRegisterOrderResponse
{
    public function __construct(
        public readonly ?string $orderId,
        public readonly ?string $formUrl,
        public readonly ?int $errorCode = null,
        public readonly ?string $errorMessage = null,
    ) {}

    public function isSuccess(): bool
    {
        return $this->orderId !== null && $this->formUrl !== null && ($this->errorCode === null || $this->errorCode === 0);
    }
}

final class AlfaOrderStatus
{
    public function __construct(
        public readonly int $orderStatus,
        public readonly ?string $paymentState,
        public readonly ?int $amountKopecks,
        public readonly ?string $paymentWay,
        public readonly ?int $errorCode = null,
        public readonly ?string $errorMessage = null,
        public readonly array $raw = [],
    ) {}

    public function isDeposited(): bool
    {
        if ($this->paymentState === 'DEPOSITED') {
            return true;
        }

        return $this->orderStatus === 2;
    }

    public function isDeclined(): bool
    {
        if ($this->paymentState === 'DECLINED') {
            return true;
        }

        return in_array($this->orderStatus, [3, 4, 6], true);
    }
}
