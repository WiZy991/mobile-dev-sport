<?php

declare(strict_types=1);

namespace App\Service\Auth;

final class OtpChannel
{
    public const SMS = 'sms';

    /** @deprecated мессенджеры больше не используются для OTP */
    public const TELEGRAM = 'telegram';
    /** @deprecated */
    public const MAX = 'max';
    /** @deprecated */
    public const WHATSAPP = 'whatsapp';

    /** @return list<string> */
    public static function all(): array
    {
        return [self::SMS];
    }

    public static function isValid(string $channel): bool
    {
        return \in_array($channel, self::all(), true);
    }
}
