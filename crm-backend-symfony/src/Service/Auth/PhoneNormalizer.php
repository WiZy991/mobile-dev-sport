<?php

declare(strict_types=1);

namespace App\Service\Auth;

final class PhoneNormalizer
{
    /** E.164 для РФ: +7XXXXXXXXXX или null, если номер неполный. */
    public static function toE164(string $raw): ?string
    {
        $digits = preg_replace('/\D+/', '', $raw) ?? '';
        if ($digits === '') {
            return null;
        }
        if (strlen($digits) === 11 && ($digits[0] === '8' || $digits[0] === '7')) {
            $digits = '7' . substr($digits, 1);
        } elseif (strlen($digits) === 10) {
            $digits = '7' . $digits;
        }
        if (strlen($digits) !== 11 || $digits[0] !== '7') {
            return null;
        }

        return '+' . $digits;
    }

    /** Последние 10 цифр без кода страны — для сравнения записей в CRM. */
    public static function national10(?string $raw): string
    {
        $e164 = self::toE164((string) $raw);
        if ($e164 === null) {
            $digits = preg_replace('/\D+/', '', (string) $raw) ?? '';

            return substr($digits, -10);
        }

        return substr($e164, -10);
    }

    /** +7 914 *** ** 21 — без полного номера. */
    public static function mask(?string $raw): string
    {
        $n = self::national10((string) $raw);
        if (strlen($n) < 10) {
            return '+7 *** *** ** **';
        }

        return sprintf('+7 %s *** ** %s', substr($n, 0, 3), substr($n, -2));
    }
}
