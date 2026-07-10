<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/** Уничтожение ПДн клиента по запросу (152-ФЗ: прекращение обработки). */
final class UserAccountDeletionService
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
    }

    public function deleteAccount(User $user): void
    {
        $id = (int) $user->getId();
        $anonEmail = sprintf('deleted-%d-%s@anon.local', $id, bin2hex(random_bytes(4)));

        $user
            ->setEmail($anonEmail)
            ->setName('Удалённый аккаунт')
            ->setPhone('')
            ->setDateOfBirth(null)
            ->setPlaceOfBirth(null)
            ->setPassportSeries(null)
            ->setPassportNumber(null)
            ->setPassportIssuedBy(null)
            ->setPassportIssueDate(null)
            ->setPassportDepartmentCode(null)
            ->setRegistrationAddress(null)
            ->setEmergencyContact(null)
            ->setGender(null)
            ->setAvatarUrl(null)
            ->setSberId(null)
            ->setAppleId(null)
            ->setPasswordHash(null)
            ->setVerified(false)
            ->setPassportVerificationStatus('none')
            ->setPassportVerifiedAt(null)
            ->setPassportVerificationProvider(null)
            ->setPassportVerificationSubject(null)
            ->setPassportVerificationAuditJson(null)
            ->setIsBlocked(true)
            ->setApiRefreshToken(null)
            ->setApiAccessToken(null)
            ->setApiAccessTokenExpiresAt(null);

        $this->em->flush();
    }
}
