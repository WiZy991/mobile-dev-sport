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
        if (in_array($this->paymentState, ['DEPOSITED', 'APPROVED'], true)) {
            return true;
        }

        if ($this->orderStatus === 2) {
            return true;
        }

        $deposited = (int) ($this->raw['paymentAmountInfo']['depositedAmount'] ?? 0);
        if ($deposited > 0 && !in_array($this->orderStatus, [3, 4, 6], true)) {
            return true;
        }

        return false;
    }

    public function isDeclined(): bool
    {
        if ($this->paymentState === 'DECLINED') {
            return true;
        }

        return in_array($this->orderStatus, [3, 4, 6], true);
    }
}
