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
    public const AUTO = 'auto';

    /** @return list<string> */
    public static function all(): array
    {
        return [self::SMS];
    }

    /**
     * Первый живой sender при channel=auto / пустом channel.
     * Сейчас OTP только через SMS (sms.ru).
     *
     * @return list<string>
     */
    public static function autoPriority(): array
    {
        return [self::SMS];
    }

    public static function isAuto(string $channel): bool
    {
        return $channel === '' || $channel === self::AUTO;
    }

    public static function isValid(string $channel): bool
    {
        return self::isAuto($channel) || \in_array($channel, self::all(), true);
    }
}
