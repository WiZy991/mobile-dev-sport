<?php

namespace App\Service\Security;

use App\Entity\AccessAlarm;
use App\Entity\Notification;
use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Entity\User;
use App\Service\Push\FcmPushSender;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Уведомления о тревоге доступа (проход вдвоём по одному QR / tailgating).
 *
 * Получатели:
 *  - персонал: только администраторы и менеджеры (по ролям);
 *  - клиент: владелец QR, по которому открыли дверь (если определён).
 *
 * Создаёт записи in-app уведомлений и шлёт push (FCM v1).
 */
final class AccessAlarmNotifier
{
    /** Роли персонала, которым уходит тревога. */
    private const STAFF_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER'];
    private const STAFF_ROLE_SUPER_ADMIN = 'ROLE_SUPER_ADMIN';
    private const STAFF_ROLE_CLUB_ALL = 'ROLE_CLUB_ALL';

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly FcmPushSender $fcmPushSender,
    ) {
    }

    public function notifyTailgating(AccessAlarm $alarm): void
    {
        $count = $alarm->getPeopleCount();
        $clubName = $alarm->getClub()?->getName() ?? 'клуб';
        $clientName = $alarm->getUser()?->getName();
        $referenceId = $alarm->getId() !== null ? (string) $alarm->getId() : null;

        $this->notifyStaff($alarm, $count, $clubName, $clientName, $referenceId);
        $this->notifyClient($alarm, $count, $clubName, $referenceId);
    }

    private function notifyStaff(
        AccessAlarm $alarm,
        int $count,
        string $clubName,
        ?string $clientName,
        ?string $referenceId,
    ): void {
        $recipients = $this->resolveStaffRecipients($alarm);
        if ($recipients === []) {
            return;
        }

        if ($alarm->getType() === AccessAlarm::TYPE_GROUP_ENTRY) {
            $title = 'Вход группой без прохода по QR';
            $body = sprintf('%s: зафиксирован вход %d чел. без прохода по QR.', $clubName, $count);
        } else {
            $title = 'Проход вдвоём по одному QR';
            $who = $clientName ? (' (' . $clientName . ')') : '';
            $body = sprintf('%s: по одному QR прошло %d чел.%s', $clubName, $count, $who);
        }

        foreach ($recipients as $staff) {
            $notification = (new StaffNotification())
                ->setStaffUser($staff)
                ->setType(StaffNotification::TYPE_ACCESS_ALARM)
                ->setTitle($title)
                ->setBody($body)
                ->setReferenceId($referenceId);
            $this->em->persist($notification);
        }
        $this->em->flush();

        $staffIds = array_values(array_filter(array_map(
            static fn (StaffUser $staff) => $staff->getId(),
            $recipients
        )));
        $this->fcmPushSender->sendToStaffUserIds(
            $staffIds,
            $title,
            $body,
            [
                'type' => StaffNotification::TYPE_ACCESS_ALARM,
                'alarmType' => $alarm->getType(),
                'alarmId' => $referenceId ?? '',
                'clubId' => (string) ($alarm->getClub()?->getId() ?? ''),
                'peopleCount' => (string) $count,
            ]
        );
    }

    private function notifyClient(
        AccessAlarm $alarm,
        int $count,
        string $clubName,
        ?string $referenceId,
    ): void {
        $user = $alarm->getUser();
        if (!$user instanceof User) {
            return;
        }

        $violation = $this->violationLabel($alarm->getType());
        $title = 'Зафиксировано нарушение прохода';
        $body = sprintf(
            'Зафиксировано нарушение: %s. По вашему QR в «%s» прошло %d чел. '
            . 'Проход предназначен только для вас — не пропускайте посторонних.',
            $violation,
            $clubName,
            $count
        );

        $notification = (new Notification())
            ->setUser($user)
            ->setType(Notification::TYPE_ACCESS_ALARM)
            ->setTitle($title)
            ->setBody($body)
            ->setReferenceId($referenceId);
        $this->em->persist($notification);
        $this->em->flush();

        $userId = $user->getId();
        if ($userId !== null) {
            $this->fcmPushSender->sendToClientUserIds(
                [$userId],
                $title,
                $body,
                [
                    'type' => Notification::TYPE_ACCESS_ALARM,
                    'alarmType' => $alarm->getType(),
                    'alarmId' => $referenceId ?? '',
                    'peopleCount' => (string) $count,
                ]
            );
        }
    }

    /** Человекочитаемое название нарушения по типу тревоги. */
    private function violationLabel(string $type): string
    {
        return match ($type) {
            AccessAlarm::TYPE_GROUP_ENTRY => 'вход группой без прохода по QR',
            default => 'проход вдвоём по одному QR',
        };
    }

    /** @return list<StaffUser> */
    private function resolveStaffRecipients(AccessAlarm $alarm): array
    {
        $all = $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true]);
        $out = [];
        $clubId = $alarm->getClub()?->getId();
        foreach ($all as $staff) {
            if (!$staff instanceof StaffUser) {
                continue;
            }
            $roles = $staff->getRoles();
            if (array_intersect($roles, self::STAFF_ROLES) === []) {
                continue;
            }
            if (\in_array(self::STAFF_ROLE_SUPER_ADMIN, $roles, true)) {
                $out[] = $staff;
                continue;
            }
            if ($this->hasClubAccess($roles, $clubId)) {
                $out[] = $staff;
            }
        }

        return $out;
    }

    /**
     * @param list<string> $roles
     */
    private function hasClubAccess(array $roles, ?int $clubId): bool
    {
        if (\in_array(self::STAFF_ROLE_CLUB_ALL, $roles, true)) {
            return true;
        }
        if ($clubId === null) {
            return false;
        }

        return \in_array('ROLE_CLUB_' . $clubId, $roles, true);
    }
}
