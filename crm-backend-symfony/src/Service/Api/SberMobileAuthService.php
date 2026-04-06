<?php

namespace App\Service\Api;

use App\Entity\User;
use App\Service\Admin\SberIdOAuthService;

/**
 * Сбер ID для клиентов мобильного приложения (отдельный redirect_uri от CRM).
 */
final class SberMobileAuthService
{
    public function __construct(
        private readonly SberIdOAuthService $sberId,
        private readonly SberMobileOAuthStateService $stateService,
        private readonly string $mobileRedirectUri,
    ) {}

    public function isReady(): bool
    {
        return $this->sberId->isConfigured() && trim($this->mobileRedirectUri) !== '';
    }

    public function getMobileRedirectUri(): string
    {
        return trim($this->mobileRedirectUri);
    }

    public function buildAuthorizeUrlForUser(User $user): string
    {
        if (!$this->isReady()) {
            throw new \RuntimeException('Сбер ID для приложения не настроен.');
        }
        $state = $this->stateService->createState($user->getId());
        $nonce = bin2hex(random_bytes(16));

        return $this->sberId->buildAuthorizeUrl($this->mobileRedirectUri, $state, $nonce);
    }

    public function parseUserIdFromState(string $state): ?int
    {
        return $this->stateService->verifyAndGetUserId($state);
    }
}
