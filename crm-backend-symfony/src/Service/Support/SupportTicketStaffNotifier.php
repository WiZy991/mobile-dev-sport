<?php

namespace App\Service\Support;

use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Entity\SupportTicket;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Push\FcmPushSender;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Уведомляет сотрудников с доступом к разделу «Обращения из приложения».
 */
final class SupportTicketStaffNotifier
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AdminMenuBuilder $adminMenuBuilder,
        private readonly FcmPushSender $fcmPushSender,
    ) {
    }

    public function notifyNewTicket(SupportTicket $ticket): void
    {
        $recipients = $this->resolveRecipients();
        if ($recipients === []) {
            return;
        }

        $clientLabel = $ticket->getUser()?->getName()
            ?? $ticket->getContactEmail()
            ?? 'Клиент';
        $title = 'Новое обращение из приложения';
        $body = sprintf(
            '%s: %s',
            $clientLabel,
            mb_substr($ticket->getSubject(), 0, 120)
        );
        $referenceId = $ticket->getId() !== null ? (string) $ticket->getId() : null;

        foreach ($recipients as $staff) {
            $notification = (new StaffNotification())
                ->setStaffUser($staff)
                ->setType(StaffNotification::TYPE_SUPPORT_TICKET)
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
            ['type' => 'support_ticket', 'ticketId' => $referenceId ?? '']
        );
    }

    /** @return list<StaffUser> */
    private function resolveRecipients(): array
    {
        $all = $this->em->getRepository(StaffUser::class)->findBy(['isActive' => true]);
        $out = [];
        foreach ($all as $staff) {
            if (!$staff instanceof StaffUser) {
                continue;
            }
            if ($this->adminMenuBuilder->isSectionAllowed($staff, 'app_support')) {
                $out[] = $staff;
            }
        }

        return $out;
    }
}
