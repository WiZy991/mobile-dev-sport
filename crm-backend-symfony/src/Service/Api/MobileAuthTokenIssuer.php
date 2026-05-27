<?php

namespace App\Service\Api;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/** Выдача пары access/refresh для мобильного API (как AuthController). */
final class MobileAuthTokenIssuer
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
    }

    /**
     * @return array{token: string, refresh_token: string, user: array<string, mixed>}
     */
    public function issue(User $user, bool $rotateRefresh): array
    {
        $accessToken = bin2hex(random_bytes(16));
        if ($rotateRefresh || $user->getApiRefreshToken() === null || $user->getApiRefreshToken() === '') {
            $user->setApiRefreshToken(bin2hex(random_bytes(16)));
        }
        $this->em->flush();

        return [
            'token' => $accessToken,
            'refresh_token' => (string) $user->getApiRefreshToken(),
            'user' => $this->userArray($user),
        ];
    }

    /** @return array<string, mixed> */
    public function userArray(User $user): array
    {
        return [
            'id' => 'user-' . $user->getId(),
            'email' => $user->getEmail(),
            'name' => $user->getName(),
            'phone' => $user->getPhone(),
            'avatar_url' => $user->getAvatarUrl(),
            'bonus_points' => $user->getBonusPoints(),
            'passport_verification_status' => $user->getPassportVerificationStatus(),
            'date_of_birth' => $user->getDateOfBirth()?->format('Y-m-d'),
            'created_at' => $user->getCreatedAt()->format('Y-m-d\TH:i:s'),
            'is_verified' => $user->isVerified(),
            'sber_id' => $user->getSberId(),
            'club_id' => $user->getClub()?->getId(),
        ];
    }
}
