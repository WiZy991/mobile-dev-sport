<?php

declare(strict_types=1);

namespace App\Service\Auth;

final class OtpDeliveryResult
{
    public function __construct(
        public readonly bool $ok,
        public readonly ?string $errorCode = null,
        public readonly ?string $errorMessage = null,
        public readonly ?string $deeplink = null,
        public readonly ?string $instruction = null,
    ) {
    }

    public static function success(?string $deeplink = null, ?string $instruction = null): self
    {
        return new self(true, null, null, $deeplink, $instruction);
    }

    public static function fail(string $code, string $message): self
    {
        return new self(false, $code, $message);
    }
}
