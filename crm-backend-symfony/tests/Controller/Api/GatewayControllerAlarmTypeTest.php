<?php

declare(strict_types=1);

namespace App\Tests\Controller\Api;

use App\Controller\Api\GatewayController;
use App\Entity\AccessAlarm;
use App\Service\Push\FcmPushSender;
use App\Service\Security\AccessAlarmNotifier;
use Doctrine\ORM\EntityManagerInterface;
use PHPUnit\Framework\TestCase;

final class GatewayControllerAlarmTypeTest extends TestCase
{
    public function testNormalizeAlarmTypeAllowsSupportedValues(): void
    {
        $controller = $this->newController();
        $normalize = new \ReflectionMethod($controller, 'normalizeAlarmType');
        $normalize->setAccessible(true);

        self::assertSame(
            AccessAlarm::TYPE_TAILGATING,
            $normalize->invoke($controller, AccessAlarm::TYPE_TAILGATING)
        );
        self::assertSame(
            AccessAlarm::TYPE_GROUP_ENTRY,
            $normalize->invoke($controller, AccessAlarm::TYPE_GROUP_ENTRY)
        );
    }

    public function testNormalizeAlarmTypeFallsBackToTailgating(): void
    {
        $controller = $this->newController();
        $normalize = new \ReflectionMethod($controller, 'normalizeAlarmType');
        $normalize->setAccessible(true);

        self::assertSame(AccessAlarm::TYPE_TAILGATING, $normalize->invoke($controller, ''));
        self::assertSame(AccessAlarm::TYPE_TAILGATING, $normalize->invoke($controller, 'unknown_type'));
    }

    private function newController(): GatewayController
    {
        $em = $this->createMock(EntityManagerInterface::class);
        return new GatewayController(
            $em,
            $this->createMock(\App\Service\Integration\SubscriptionGateResolver::class),
            new AccessAlarmNotifier($em, new FcmPushSender($em)),
        );
    }
}

