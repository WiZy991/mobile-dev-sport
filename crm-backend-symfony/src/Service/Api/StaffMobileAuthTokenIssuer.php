<?php

namespace App\Service\Api;

use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;

final class StaffMobileAuthTokenIssuer
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly int $accessTokenTtlSeconds = 3600,
    ) {
    }

    /** @return array{token: string, refresh_token: string, user: array<string, mixed>} */
    public function issue(StaffUser $user, bool $rotateRefresh): array
    {
        $accessToken = bin2hex(random_bytes(32));
        $user
            ->setApiAccessToken($accessToken)
            ->setApiAccessTokenExpiresAt(new \DateTimeImmutable('+' . $this->accessTokenTtlSeconds . ' seconds'));

        if ($rotateRefresh || $user->getApiRefreshToken() === null || $user->getApiRefreshToken() === '') {
            $user->setApiRefreshToken(bin2hex(random_bytes(32)));
        }

        $this->em->flush();

        return [
            'token' => $accessToken,
            'refresh_token' => (string) $user->getApiRefreshToken(),
            'user' => $this->staffUserArray($user),
        ];
    }

    public function revokeSession(StaffUser $user): void
    {
        $user
            ->setApiAccessToken(null)
            ->setApiAccessTokenExpiresAt(null)
            ->setApiRefreshToken(null);
        $this->em->flush();
    }

    /** @return array<string, mixed> */
    public function staffUserArray(StaffUser $user): array
    {
        return [
            'id' => 'staff-' . $user->getId(),
            'email' => $user->getEmail(),
            'name' => $user->getName(),
            'roles' => $user->getRoles(),
            'is_active' => $user->isActive(),
            'created_at' => $user->getCreatedAt()->format('Y-m-d\TH:i:s'),
        ];
    }
}
