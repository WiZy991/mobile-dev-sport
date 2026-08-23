<?php

declare(strict_types=1);

namespace App\Service;

/**
 * Календарь клуба (Доброзал / Владивосток).
 * Access logs остаются в UTC; аренда тренера и «сегодня» для бизнеса — по Владивостоку.
 */
final class ClubTimezone
{
    public const ID = 'Asia/Vladivostok';

    public static function zone(): \DateTimeZone
    {
        return new \DateTimeZone(self::ID);
    }

    public static function now(): \DateTimeImmutable
    {
        return new \DateTimeImmutable('now', self::zone());
    }
}
