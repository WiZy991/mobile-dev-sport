<?php

declare(strict_types=1);

namespace App\Service\Integration;

/**
 * Компактный 9-значный QR для считывателей PERCo в режиме Wiegand.
 *
 * Формат: UUUUUTTTC
 * - UUUUU — user id % 100000 (5 цифр)
 * - TTT   — слот времени floor(ms / 15s) % 1000 (3 цифры)
 * - C     — контрольная цифра (Luhn mod 10 по первым 8 цифрам)
 *
 * Синхронно с QRCodeGenerator (iOS) и QrCodeViewModel (Android).
 */
final class WiegandEntryQrCodec
{
    public const SLOT_MS = 15_000;
    public const SLOT_MOD = 1000;
    public const USER_MOD = 100_000;
    public const LENGTH = 9;
    public const MIN_WIEGAND_LEN = 5;

    public static function normalize(string $qr): ?string
    {
        $q = trim($qr);
        if (!ctype_digit($q)) {
            return null;
        }
        $len = \strlen($q);
        if ($len < self::MIN_WIEGAND_LEN || $len > self::LENGTH) {
            return null;
        }

        return str_pad($q, self::LENGTH, '0', \STR_PAD_LEFT);
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
        $body = \sprintf('%05d%03d', $uid, $slot);
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

        $userId = (int) substr($q, 0, 5);
        $timeSegment = substr($q, 5, 3);
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

    public static function verifyChecksum(string $nineDigits): bool
    {
        $q = self::normalize($nineDigits);
        if ($q === null) {
            return false;
        }

        return self::checksumDigit(substr($q, 0, 8)) === (int) $q[8];
    }

    public static function checksumDigit(string $eightDigits): int
    {
        if (\strlen($eightDigits) !== 8 || !ctype_digit($eightDigits)) {
            return 0;
        }

        $sum = 0;
        $rev = strrev($eightDigits);
        for ($i = 0; $i < 8; ++$i) {
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
}
