<?php

declare(strict_types=1);

namespace App\Service\Auth;

final class PhoneOtpChallengeContext
{
    public function __construct(
        public readonly string $deeplinkToken,
    ) {
    }
}
