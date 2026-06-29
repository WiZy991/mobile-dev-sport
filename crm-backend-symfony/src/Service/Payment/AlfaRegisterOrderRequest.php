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
