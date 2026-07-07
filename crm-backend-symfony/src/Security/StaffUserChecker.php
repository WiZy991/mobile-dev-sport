<?php

namespace App\Security;

use App\Entity\StaffUser;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Exception\CustomUserMessageAccountStatusException;
use Symfony\Component\Security\Core\User\UserCheckerInterface;
use Symfony\Component\Security\Core\User\UserInterface;

final class StaffUserChecker implements UserCheckerInterface
{
    public function checkPreAuth(UserInterface $user): void
    {
        if (!$user instanceof StaffUser) {
            return;
        }
        if (!$user->isActive()) {
            throw new CustomUserMessageAccountStatusException('Учётная запись отключена.');
        }

        $organization = $user->getOrganization();
        if ($organization !== null) {
            if (!$organization->isActive()) {
                throw new CustomUserMessageAccountStatusException('Доступ к CRM отключён. Свяжитесь с поддержкой WorldCashFit.');
            }
            if ($organization->isDemoExpired()) {
                throw new CustomUserMessageAccountStatusException('Демо-период истёк. Оформите лицензию на worldcashfit.ru');
            }
        }
    }

    public function checkPostAuth(UserInterface $user, ?TokenInterface $token = null): void
    {
    }
}
