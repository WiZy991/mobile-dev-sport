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
        self::assertSame(9, \strlen($payload));
        self::assertTrue(WiegandEntryQrCodec::isPayload($payload));

        $parsed = WiegandEntryQrCodec::parse($payload, $ms + 1_000);
        self::assertNotNull($parsed);
        self::assertSame(42, $parsed['user_id']);
        self::assertSame($ms - ($ms % WiegandEntryQrCodec::SLOT_MS), $parsed['timestamp_ms']);
    }

    public function testInvalidChecksumRejected(): void
    {
        $payload = WiegandEntryQrCodec::encode(1, 1_700_000_000_000);
        $broken = substr($payload, 0, 8) . ((int) $payload[8] + 1) % 10;
        self::assertNull(WiegandEntryQrCodec::parse($broken));
    }

    public function testParseEightDigitsWithLeadingZeroStripped(): void
    {
        $full = WiegandEntryQrCodec::encode(5133, 1_700_000_000_000);
        self::assertSame(9, \strlen($full));
        $stripped = ltrim($full, '0');
        self::assertGreaterThanOrEqual(8, \strlen($stripped));

        $parsed = WiegandEntryQrCodec::parse($stripped, 1_700_000_000_000);
        self::assertNotNull($parsed);
        self::assertSame(5133, $parsed['user_id']);
    }
}
