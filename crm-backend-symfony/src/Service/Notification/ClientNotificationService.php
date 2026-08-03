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

    /**
     * @param bool $force      создать in-app уведомление даже при выключенной настройке типа
     * @param bool $forceEmail отправить письмо независимо от настроек (транзакционные письма — чеки об оплате)
     */
    public function notify(
        User $user,
        string $type,
        string $title,
        string $body,
        ?string $referenceId = null,
        bool $force = false,
        bool $forceEmail = false,
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

        $userId = $user->getId();
        $sendPush = $this->preferences->allowsPush($user, $type) || ($force && $user->isNotifyPushEnabled());
        $email = $user->getEmail();
        $sendEmail = $email !== ''
            && ($forceEmail || $this->preferences->allowsEmail($user, $type) || ($force && $user->isNotifyEmailEnabled()));

        // Не держим HTTP-ответ на FCM/SMTP — отправляем после flush ответа.
        if (($sendPush && $userId !== null) || $sendEmail) {
            $fcm = $this->fcmPushSender;
            $mailer = $this->emailNotifier;
            register_shutdown_function(static function () use (
                $sendPush,
                $sendEmail,
                $userId,
                $email,
                $title,
                $body,
                $type,
                $referenceId,
                $fcm,
                $mailer,
            ): void {
                try {
                    if ($sendPush && $userId !== null) {
                        $fcm->sendToClientUserIds(
                            [$userId],
                            $title,
                            $body,
                            ['type' => $type, 'referenceId' => $referenceId ?? ''],
                        );
                    }
                    if ($sendEmail) {
                        $mailer->send(
                            $email,
                            $title,
                            $body . "\n\n— WorldCashFit",
                        );
                    }
                } catch (\Throwable) {
                }
            });
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
