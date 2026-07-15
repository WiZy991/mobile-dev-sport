<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\Notification;
use App\Entity\PushToken;
use App\Entity\User;
use App\Service\Push\FcmPushSender;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Единая точка отправки клиентских уведомлений с учётом настроек пользователя.
 */
final class ClientNotificationService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly UserNotificationPreferenceResolver $preferences,
        private readonly FcmPushSender $fcmPushSender,
        private readonly ClientEmailNotifier $emailNotifier,
    ) {
    }

    public function notify(
        User $user,
        string $type,
        string $title,
        string $body,
        ?string $referenceId = null,
        bool $force = false,
    ): void {
        if (!$force && !$this->preferences->allowsInApp($user, $type)) {
            return;
        }

        if ($referenceId !== null && $referenceId !== '') {
            $existing = $this->em->getRepository(Notification::class)->findOneBy([
                'user' => $user,
                'type' => $type,
                'referenceId' => $referenceId,
            ]);
            if ($existing instanceof Notification) {
                return;
            }
        }

        $notification = (new Notification())
            ->setUser($user)
            ->setType($type)
            ->setTitle($title)
            ->setBody($body)
            ->setReferenceId($referenceId);
        $this->em->persist($notification);
        $this->em->flush();

        if ($this->preferences->allowsPush($user, $type) || ($force && $user->isNotifyPushEnabled())) {
            $userId = $user->getId();
            if ($userId !== null) {
                $this->fcmPushSender->sendToClientUserIds(
                    [$userId],
                    $title,
                    $body,
                    ['type' => $type, 'referenceId' => $referenceId ?? ''],
                );
            }
        }

        if ($this->preferences->allowsEmail($user, $type) || ($force && $user->isNotifyEmailEnabled())) {
            $this->emailNotifier->send(
                $user->getEmail(),
                $title,
                $body . "\n\n— WorldCashFit",
            );
        }
    }

    public function clearPushTokens(User $user): void
    {
        $tokens = $this->em->getRepository(PushToken::class)->findBy(['user' => $user]);
        foreach ($tokens as $token) {
            $this->em->remove($token);
        }
        $this->em->flush();
    }

    /** @return array<string, bool> */
    public static function serializeSettings(User $user): array
    {
        return [
            'push_enabled' => $user->isNotifyPushEnabled(),
            'email_enabled' => $user->isNotifyEmailEnabled(),
            'training_reminders' => $user->isNotifyTrainingReminders(),
            'schedule_changes' => $user->isNotifyScheduleChanges(),
            'promo_notifications' => $user->isNotifyPromo(),
        ];
    }

    public function applySettings(User $user, array $data): void
    {
        if (array_key_exists('push_enabled', $data)) {
            $user->setNotifyPushEnabled((bool) $data['push_enabled']);
        }
        if (array_key_exists('email_enabled', $data)) {
            $user->setNotifyEmailEnabled((bool) $data['email_enabled']);
        }
        if (array_key_exists('training_reminders', $data)) {
            $user->setNotifyTrainingReminders((bool) $data['training_reminders']);
        }
        if (array_key_exists('schedule_changes', $data)) {
            $user->setNotifyScheduleChanges((bool) $data['schedule_changes']);
        }
        if (array_key_exists('promo_notifications', $data)) {
            $user->setNotifyPromo((bool) $data['promo_notifications']);
        }

        if (!$user->isNotifyPushEnabled()) {
            $this->clearPushTokens($user);
        }
    }
}
