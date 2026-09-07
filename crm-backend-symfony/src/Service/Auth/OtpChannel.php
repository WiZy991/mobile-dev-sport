<?php

declare(strict_types=1);

namespace App\Service\Auth;

final class OtpChannel
{
    public const TELEGRAM = 'telegram';
    public const MAX = 'max';
    public const WHATSAPP = 'whatsapp';

    /** @return list<string> */
    public static function all(): array
    {
        return [self::TELEGRAM, self::MAX, self::WHATSAPP];
    }

    public static function isValid(string $channel): bool
    {
        return \in_array($channel, self::all(), true);
    }
}
