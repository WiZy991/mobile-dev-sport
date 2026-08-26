<?php

declare(strict_types=1);

namespace App\Tests\Service\Integration;

use App\Service\Integration\WiegandEntryQrCodec;
use PHPUnit\Framework\TestCase;

final class WiegandEntryQrCodecTest extends TestCase
{
    public function testEncodeDecodeRoundTrip(): void
    {
        $ms = 1_700_000_000_000;
        $payload = WiegandEntryQrCodec::encode(42, $ms);
        self::assertSame(7, \strlen($payload));
        self::assertLessThanOrEqual(0xFFFFFF, (int) $payload);
        self::assertTrue(WiegandEntryQrCodec::isPayload($payload));

        $parsed = WiegandEntryQrCodec::parse($payload, $ms + 1_000);
        self::assertNotNull($parsed);
        self::assertSame(42, $parsed['user_id']);
    }

    public function testFitsInWiegand26Bits(): void
    {
        $ms = (int) round(microtime(true) * 1000);
        for ($uid = 0; $uid < 10_000; $uid += 137) {
            $payload = WiegandEntryQrCodec::encode($uid, $ms);
            self::assertLessThanOrEqual(0xFFFFFF, (int) $payload, $payload);
        }
    }

    public function testLeadingZerosStrippedStillParse(): void
    {
        $ms = 1_700_000_000_000;
        $full = WiegandEntryQrCodec::encode(42, $ms);
        $stripped = ltrim($full, '0') ?: '0';
        $parsed = WiegandEntryQrCodec::parse($stripped, $ms);
        self::assertNotNull($parsed);
        self::assertSame(42, $parsed['user_id']);
    }

    public function testInvalidChecksumRejected(): void
    {
        $payload = WiegandEntryQrCodec::encode(1, 1_700_000_000_000);
        $broken = substr($payload, 0, 6) . ((int) $payload[6] + 1) % 10;
        self::assertNull(WiegandEntryQrCodec::parse($broken));
    }

    public function testUserIdWrapsAt10k(): void
    {
        $ms = 1_700_000_000_000;
        $payload = WiegandEntryQrCodec::encode(10_042, $ms);
        self::assertStringStartsWith('0042', $payload);
    }
}
