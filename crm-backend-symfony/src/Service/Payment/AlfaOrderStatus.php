<?php

namespace App\Service\Payment;

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
