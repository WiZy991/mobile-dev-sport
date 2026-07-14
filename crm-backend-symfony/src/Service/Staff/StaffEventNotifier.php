<?php

declare(strict_types=1);

namespace App\Service\Staff;

use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Push\FcmPushSender;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Центральная рассылка уведомлений персоналу CRM (in-app + FCM).
 */
final class StaffEventNotifier
{
    private const ADMIN_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_MANAGER'];

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly FcmPushSender $fcmPushSender,
    ) {
    }

    public function notifyBySection(
        string $section,
        string $type,
        string $title,
        string $body,
        ?string $referenceId = null,
    ): void {
        $recipients = $this->resolveBySection($section);
        $this->dispatch($recipients, $type, $title, $body, $referenceId);
    }

    public function notifyAdmins(
        string $type,
        string $title,
        string $body,
        ?string $referenceId = null,
    ): void {
        $recipients = $this->resolveAdmins();
        $this->dispatch($recipients, $type, $title, $body, $referenceId);
    }

    public function notifyAllActiveStaff(
        string $type,
        string $title,
        string $body,
        ?string $referenceId = null,
    ): void {
        $all = $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true]);
        $recipients = array_values(array_filter(
            $all,
            static fn ($staff) => $staff instanceof StaffUser
        ));
        $this->dispatch($recipients, $type, $title, $body, $referenceId);
    }

    /**
     * @param list<StaffUser> $recipients
     */
    private function dispatch(
        array $recipients,
        string $type,
        string $title,
        string $body,
        ?string $referenceId,
    ): void {
        if ($recipients === []) {
            return;
        }

        foreach ($recipients as $staff) {
            $notification = (new StaffNotification())
                ->setStaffUser($staff)
                ->setType($type)
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
            ['type' => $type, 'referenceId' => $referenceId ?? '']
        );
    }

    /** @return list<StaffUser> */
    private function resolveBySection(string $section): array
    {
        $all = $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true]);
        $out = [];
        foreach ($all as $staff) {
            if ($staff instanceof StaffUser && $this->adminMenuBuilder->isSectionAllowed($staff, $section)) {
                $out[] = $staff;
            }
        }

        return $out;
    }

    /** @return list<StaffUser> */
    private function resolveAdmins(): array
    {
        $all = $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true]);
        $out = [];
        foreach ($all as $staff) {
            if (!$staff instanceof StaffUser) {
                continue;
            }
            if (array_intersect($staff->getRoles(), self::ADMIN_ROLES) !== []) {
                $out[] = $staff;
            }
        }

        return $out;
    }
}
