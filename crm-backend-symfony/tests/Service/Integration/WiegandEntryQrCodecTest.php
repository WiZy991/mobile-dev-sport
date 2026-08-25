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

    public function testUserIdWrapsAt100k(): void
    {
        $ms = 1_700_000_000_000;
        $payload = WiegandEntryQrCodec::encode(100_042, $ms);
        self::assertStringStartsWith('00042', $payload);
    }
}
