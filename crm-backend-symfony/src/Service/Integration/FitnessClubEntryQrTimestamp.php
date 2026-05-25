<?php

declare(strict_types=1);

namespace App\Service\Integration;

/**
 * Время в последнем сегменте QR входа FITNESSCLUB:ENTRY:(user):(time).
 *
 * Исторически (time) — Unix в миллисекундах (12–13 десятичных цифр). У части контроллеров PERCo/C01
 * длина поля id в событии card ограничена (часто 32 символа на всю строку) — хвост обрезается,
 * и CRM получает битую метку и отвечает qr_expired. Компактный формат: ровно 7 символов base62 (те же ms).
 */
final class FitnessClubEntryQrTimestamp
{
    private const ALPHABET = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

    /**
     * @return int|null Unix time в миллисекундах или null, если сегмент не распознан
     */
    public static function parseToUnixMs(string $segment): ?int
    {
        $seg = trim($segment);
        if ($seg === '') {
            return null;
        }
        // Полные миллисекунды десятичной записью (как в старых клиентах приложения, обычно 12–13 цифр).
        if (preg_match('/^\d{11,}$/', $seg) === 1) {
            return (int) $seg;
        }
        // Компактный формат под лимит длины id (ровно 7 символов base62).
        if (preg_match('/^[0-9A-Za-z]{7}$/', $seg) !== 1) {
            return null;
        }

        return self::fromBase62($seg);
    }

    private static function fromBase62(string $s): int
    {
        $n = 0;
        $base = \strlen(self::ALPHABET);
        $len = \strlen($s);
        for ($i = 0; $i < $len; ++$i) {
            $p = strpos(self::ALPHABET, $s[$i]);
            if ($p === false) {
                return 0;
            }
            $n = $n * $base + $p;
        }

        return $n;
    }
}
