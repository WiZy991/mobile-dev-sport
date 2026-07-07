<?php

namespace App\Security;

use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Security\Core\Authentication\Token\TokenInterface;
use Symfony\Component\Security\Core\Exception\CustomUserMessageAccountStatusException;
use Symfony\Component\Security\Core\User\UserCheckerInterface;
use Symfony\Component\Security\Core\User\UserInterface;

final class StaffUserChecker implements UserCheckerInterface
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

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
            if ($organization->isSubscriptionExpired()) {
                // Автоматически отключаем CRM, когда срок подписки истёк.
                $organization->setIsActive(false);
                $this->em->flush();
                throw new CustomUserMessageAccountStatusException(
                    'Срок подписки закончился. Доступ в CRM приостановлен. Обратитесь к менеджеру WorldCashFit для продления.'
                );
            }
        }
    }

    public function checkPostAuth(UserInterface $user, ?TokenInterface $token = null): void
    {
    }
}
