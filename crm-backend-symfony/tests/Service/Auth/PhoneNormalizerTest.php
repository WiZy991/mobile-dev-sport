<?php

declare(strict_types=1);

namespace App\Tests\Service\Auth;

use App\Service\Auth\PhoneNormalizer;
use PHPUnit\Framework\TestCase;

final class PhoneNormalizerTest extends TestCase
{
    public function testToE164(): void
    {
        self::assertSame('+79141234567', PhoneNormalizer::toE164('8 (914) 123-45-67'));
        self::assertSame('+79141234567', PhoneNormalizer::toE164('79141234567'));
        self::assertSame('+79141234567', PhoneNormalizer::toE164('9141234567'));
        self::assertNull(PhoneNormalizer::toE164('123'));
    }

    public function testMask(): void
    {
        self::assertSame('+7 914 *** ** 67', PhoneNormalizer::mask('+79141234567'));
    }
}
