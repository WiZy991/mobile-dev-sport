<?php

declare(strict_types=1);

namespace App\Tests\Service\Auth;

use App\Service\Auth\OtpChannel;
use PHPUnit\Framework\TestCase;

final class OtpChannelTest extends TestCase
{
    public function testAutoAcceptsEmptyAndAuto(): void
    {
        self::assertTrue(OtpChannel::isAuto(''));
        self::assertTrue(OtpChannel::isAuto(OtpChannel::AUTO));
        self::assertTrue(OtpChannel::isValid(''));
        self::assertTrue(OtpChannel::isValid('auto'));
        self::assertTrue(OtpChannel::isValid(OtpChannel::SMS));
        self::assertFalse(OtpChannel::isAuto(OtpChannel::SMS));
    }

    public function testAutoPriorityIsSms(): void
    {
        self::assertSame([OtpChannel::SMS], OtpChannel::autoPriority());
    }
}
