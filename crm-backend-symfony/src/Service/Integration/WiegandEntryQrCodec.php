<?php

declare(strict_types=1);

namespace App\Service\Integration;

/**
 * Компактный 7-значный QR для считывателей PERCo в режиме Wiegand-26.
 *
 * Формат: UUUUTTC
 * - UUUU — user id % 10000 (4 цифры)
 * - TT   — слот времени floor(ms / 15s) % 100 (2 цифры)
 * - C    — контрольная цифра (Luhn mod 10 по первым 6 цифрам)
 *
 * Число всегда ≤ 9_999_999 < 2^24, поэтому Wiegand-26 не обрезает код
 * (9-значный формат давал обрезку вида 51338580 → 1006932).
 *
 * Синхронно с QRCodeGenerator (iOS) и QrCodeViewModel (Android).
 */
final class WiegandEntryQrCodec
{
    public const SLOT_MS = 15_000;
    public const SLOT_MOD = 100;
    public const USER_MOD = 10_000;
    public const LENGTH = 7;
    public const MIN_WIEGAND_LEN = 4;
    /** Старый 9-значный формат (до лимита Wiegand-26) — ещё принимаем, если пришла полная строка. */
    public const LEGACY_LENGTH = 9;

    public static function normalize(string $qr): ?string
    {
        $q = trim($qr);
        if (!ctype_digit($q)) {
            return null;
        }
        $len = \strlen($q);
        if ($len >= self::MIN_WIEGAND_LEN && $len <= self::LENGTH) {
            return str_pad($q, self::LENGTH, '0', \STR_PAD_LEFT);
        }
        if ($len > self::LENGTH && $len <= self::LEGACY_LENGTH) {
            return str_pad($q, self::LEGACY_LENGTH, '0', \STR_PAD_LEFT);
        }

        return null;
    }

    public static function isPayload(string $qr): bool
    {
        $normalized = self::normalize($qr);
        if ($normalized === null) {
            return false;
        }

        return self::verifyChecksum($normalized);
    }

    public static function encode(int $userId, int $timestampMs): string
    {
        $uid = $userId % self::USER_MOD;
        $slot = intdiv(max(0, $timestampMs), self::SLOT_MS) % self::SLOT_MOD;
        $body = \sprintf('%04d%02d', $uid, $slot);
        $check = self::checksumDigit($body);

        return $body . (string) $check;
    }

    /**
     * @return array{user_id: int, timestamp_ms: int, time_segment: string}|null
     */
    public static function parse(string $qr, ?int $nowMs = null): ?array
    {
        $q = self::normalize($qr);
        if ($q === null || !self::verifyChecksum($q)) {
            return null;
        }

        if (\strlen($q) === self::LEGACY_LENGTH) {
            return self::parseLegacyNine($q, $nowMs);
        }

        $userId = (int) substr($q, 0, 4);
        $timeSegment = substr($q, 4, 2);
        $slot = (int) $timeSegment;
        $timestampMs = self::resolveTimestampMs($slot, $nowMs);
        if ($timestampMs === null) {
            return null;
        }

        return [
            'user_id' => $userId,
            'timestamp_ms' => $timestampMs,
            'time_segment' => $timeSegment,
        ];
    }

    public static function verifyChecksum(string $digits): bool
    {
        $q = self::normalize($digits);
        if ($q === null) {
            return false;
        }
        $bodyLen = \strlen($q) - 1;

        return self::checksumDigit(substr($q, 0, $bodyLen)) === (int) $q[$bodyLen];
    }

    public static function checksumDigit(string $bodyDigits): int
    {
        if ($bodyDigits === '' || !ctype_digit($bodyDigits)) {
            return 0;
        }

        $sum = 0;
        $rev = strrev($bodyDigits);
        $len = \strlen($rev);
        for ($i = 0; $i < $len; ++$i) {
            $n = (int) $rev[$i];
            if ($i % 2 === 0) {
                $n *= 2;
                if ($n > 9) {
                    $n -= 9;
                }
            }
            $sum += $n;
        }

        return (10 - $sum % 10) % 10;
    }

    public static function resolveTimestampMs(int $slot, ?int $nowMs = null): ?int
    {
        $nowMs ??= (int) round(microtime(true) * 1000);
        $currentBase = intdiv($nowMs, self::SLOT_MS);

        for ($offset = -3; $offset <= 3; ++$offset) {
            $base = $currentBase + $offset;
            if ($base < 0) {
                continue;
            }
            if ($base % self::SLOT_MOD !== $slot) {
                continue;
            }
            $candidate = $base * self::SLOT_MS;
            if (abs($nowMs - $candidate) <= self::SLOT_MS + 5_000) {
                return $candidate;
            }
        }

        return null;
    }

    /**
     * @return array{user_id: int, timestamp_ms: int, time_segment: string}|null
     */
    private static function parseLegacyNine(string $q, ?int $nowMs): ?array
    {
        $userId = (int) substr($q, 0, 5);
        $timeSegment = substr($q, 5, 3);
        $slot = (int) $timeSegment;
        // Старый SLOT_MOD = 1000
        $nowMs ??= (int) round(microtime(true) * 1000);
        $currentBase = intdiv($nowMs, self::SLOT_MS);
        $timestampMs = null;
        for ($offset = -3; $offset <= 3; ++$offset) {
            $base = $currentBase + $offset;
            if ($base < 0) {
                continue;
            }
            if ($base % 1000 !== $slot) {
                continue;
            }
            $candidate = $base * self::SLOT_MS;
            if (abs($nowMs - $candidate) <= self::SLOT_MS + 5_000) {
                $timestampMs = $candidate;
                break;
            }
        }
        if ($timestampMs === null) {
            return null;
        }

        return [
            'user_id' => $userId,
            'timestamp_ms' => $timestampMs,
            'time_segment' => $timeSegment,
        ];
    }
}
