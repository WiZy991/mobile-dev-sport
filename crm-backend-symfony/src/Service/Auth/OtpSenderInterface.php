<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Symfony\Component\DependencyInjection\Attribute\AutoconfigureTag;

#[AutoconfigureTag('app.otp_sender')]
interface OtpSenderInterface
{
    public function channel(): string;

    public function isConfigured(): bool;

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult;
}
