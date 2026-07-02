<?php

declare(strict_types=1);

namespace App\Tests\Service\Security;

use App\Entity\AccessAlarm;
use App\Entity\Club;
use App\Entity\StaffUser;
use App\Service\Push\FcmPushSender;
use App\Service\Security\AccessAlarmNotifier;
use Doctrine\ORM\EntityManagerInterface;
use Doctrine\ORM\EntityRepository;
use PHPUnit\Framework\TestCase;

final class AccessAlarmNotifierTest extends TestCase
{
    public function testResolveStaffRecipientsScopedByClubRole(): void
    {
        $alarm = (new AccessAlarm())
            ->setClub($this->clubWithId(7))
            ->setType(AccessAlarm::TYPE_TAILGATING)
            ->setPeopleCount(2);

        $superAdmin = (new StaffUser())->setEmail('super@test.local')->setName('Super')->setRoles(['ROLE_SUPER_ADMIN']);
        $clubAdmin = (new StaffUser())->setEmail('club@test.local')->setName('Club')->setRoles(['ROLE_ADMIN', 'ROLE_CLUB_7']);
        $otherClub = (new StaffUser())->setEmail('other@test.local')->setName('Other')->setRoles(['ROLE_ADMIN', 'ROLE_CLUB_8']);
        $globalClub = (new StaffUser())->setEmail('global@test.local')->setName('Global')->setRoles(['ROLE_MANAGER', 'ROLE_CLUB_ALL']);

        $repo = $this->createMock(EntityRepository::class);
        $repo->method('findBy')->with(['isActive' => true])->willReturn([
            $superAdmin,
            $clubAdmin,
            $otherClub,
            $globalClub,
        ]);

        $em = $this->createMock(EntityManagerInterface::class);
        $em->method('getRepository')->willReturn($repo);

        $notifier = new AccessAlarmNotifier($em, new FcmPushSender($em));

        $method = new \ReflectionMethod($notifier, 'resolveStaffRecipients');
        $method->setAccessible(true);
        $recipients = $method->invoke($notifier, $alarm);

        self::assertCount(3, $recipients);
        self::assertContains($superAdmin, $recipients);
        self::assertContains($clubAdmin, $recipients);
        self::assertContains($globalClub, $recipients);
        self::assertNotContains($otherClub, $recipients);
    }

    public function testViolationLabelDiffersByAlarmType(): void
    {
        $em = $this->createMock(EntityManagerInterface::class);
        $notifier = new AccessAlarmNotifier($em, new FcmPushSender($em));
        $method = new \ReflectionMethod($notifier, 'violationLabel');
        $method->setAccessible(true);

        $tailgating = $method->invoke($notifier, AccessAlarm::TYPE_TAILGATING);
        $groupEntry = $method->invoke($notifier, AccessAlarm::TYPE_GROUP_ENTRY);

        self::assertNotSame($tailgating, $groupEntry);
        self::assertStringContainsString('вдво', mb_strtolower((string) $tailgating));
        self::assertStringContainsString('без прохода по qr', mb_strtolower((string) $groupEntry));
    }

    private function clubWithId(int $id): Club
    {
        $club = (new Club())->setName('Club #' . $id)->setAddress('Address');
        $ref = new \ReflectionProperty($club, 'id');
        $ref->setAccessible(true);
        $ref->setValue($club, $id);

        return $club;
    }
}

