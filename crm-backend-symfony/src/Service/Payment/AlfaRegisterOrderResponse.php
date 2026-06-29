<?php

namespace App\Service\Payment;

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
