<?php

namespace App\Service\Payment;

/**
 * Ответ API ЮKassa. Не сервис контейнера.
 */
final class YooKassaApiResult
{
    /**
     * @param array<string, mixed>|null $payment
     */
    public function __construct(
        public readonly bool $ok,
        public readonly ?array $payment = null,
        public readonly ?string $error = null,
        public readonly ?string $errorCode = null,
        public readonly int $httpStatus = 200,
    ) {}
}
