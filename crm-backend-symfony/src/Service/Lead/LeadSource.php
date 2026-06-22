<?php

declare(strict_types=1);

namespace App\Service\Lead;

/** Источники лидов (ключ → подпись в CRM). */
final class LeadSource
{
    public const SITE = 'site';
    public const INSTAGRAM = 'instagram';
    public const VK = 'vk';
    public const TELEGRAM = 'telegram';
    public const REFERRAL = 'referral';
    public const GUEST_PASS = 'guest_pass';
    public const SUPPORT = 'support';
    public const CALL = 'call';
    public const WALK_IN = 'walk_in';
    public const OTHER = 'other';

    /** @return array<string, string> */
    public static function labels(): array
    {
        return [
            self::SITE => 'Сайт',
            self::INSTAGRAM => 'Instagram',
            self::VK => 'ВКонтакте',
            self::TELEGRAM => 'Telegram',
            self::REFERRAL => 'Рекомендация',
            self::GUEST_PASS => 'Гостевой пропуск',
            self::SUPPORT => 'Поддержка',
            self::CALL => 'Звонок',
            self::WALK_IN => 'Пришёл в клуб',
            self::OTHER => 'Другое',
        ];
    }

    public static function label(?string $key): string
    {
        if ($key === null || $key === '') {
            return '—';
        }

        return self::labels()[$key] ?? $key;
    }

    /** @return list<string> */
    public static function keys(): array
    {
        return array_keys(self::labels());
    }

    public static function isValid(?string $key): bool
    {
        return $key !== null && $key !== '' && isset(self::labels()[$key]);
    }

    /** CSS-модификатор для бейджа источника. */
    public static function badgeClass(?string $key): string
    {
        return match ($key) {
            self::SITE => 'crm-badge-source-site',
            self::INSTAGRAM => 'crm-badge-source-instagram',
            self::VK => 'crm-badge-source-vk',
            self::TELEGRAM => 'crm-badge-source-telegram',
            self::REFERRAL => 'crm-badge-source-referral',
            self::GUEST_PASS => 'crm-badge-source-guest',
            self::SUPPORT => 'crm-badge-source-support',
            self::CALL => 'crm-badge-source-call',
            self::WALK_IN => 'crm-badge-source-walkin',
            default => 'crm-badge-source-other',
        };
    }
}
