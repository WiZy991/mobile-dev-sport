<?php

declare(strict_types=1);

namespace App\Service\Notification;

use App\Entity\Notification;
use App\Entity\User;

final class UserNotificationPreferenceResolver
{
    public function allowsInApp(User $user, string $type): bool
    {
        return match ($type) {
            Notification::TYPE_TRAINING_REMINDER => $user->isNotifyTrainingReminders(),
            Notification::TYPE_SCHEDULE_CHANGE => $user->isNotifyScheduleChanges(),
            Notification::TYPE_PROMO => $user->isNotifyPromo(),
            Notification::TYPE_SPOT_FREED => $user->isNotifyTrainingReminders(),
            default => true,
        };
    }

    public function allowsPush(User $user, string $type): bool
    {
        if (!$user->isNotifyPushEnabled()) {
            return false;
        }

        return $this->allowsInApp($user, $type);
    }

    public function allowsEmail(User $user, string $type): bool
    {
        if (!$user->isNotifyEmailEnabled()) {
            return false;
        }
        if (!$this->isDeliverableEmail($user->getEmail())) {
            return false;
        }

        return $this->allowsInApp($user, $type);
    }

    private function isDeliverableEmail(string $email): bool
    {
        $email = mb_strtolower(trim($email));
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return false;
        }
        if (str_contains($email, '@example.local')
            || str_contains($email, '@users.worldcashfit.ru')
            || str_ends_with($email, '@placeholder.local')) {
            return false;
        }

        return true;
    }
}
