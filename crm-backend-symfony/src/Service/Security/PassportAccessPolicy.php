<?php

declare(strict_types=1);

namespace App\Service\Security;

use App\Entity\StaffUser;
use Symfony\Component\Security\Core\User\UserInterface;

/**
 * Кто в CRM может видеть/выгружать паспортные данные клиентов (ПДн от Сбер ID).
 */
final class PassportAccessPolicy
{
    private const PASSPORT_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN'];

    public function canViewPassportDetails(?UserInterface $user): bool
    {
        if (!$user instanceof StaffUser) {
            return false;
        }

        return (bool) array_intersect($user->getRoles(), self::PASSPORT_ROLES);
    }

    public function canExportPassportDetails(?UserInterface $user): bool
    {
        return $this->canViewPassportDetails($user);
    }
}
