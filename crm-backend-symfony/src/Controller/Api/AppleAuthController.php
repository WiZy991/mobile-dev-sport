<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Service\Api\AppleMobileAuthService;
use App\Service\Api\MobileAuthTokenIssuer;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;

final class AppleAuthController extends AbstractController
{
    public function __construct(
        private readonly AppleMobileAuthService $appleAuth,
        private readonly MobileAuthTokenIssuer $mobileTokens,
    ) {
    }

    /** Sign in with Apple: клиент передаёт identity_token (JWT) и опционально имя при первом входе. */
    public function signIn(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $identityToken = trim((string) ($data['identity_token'] ?? $data['identityToken'] ?? ''));

        if ($identityToken === '') {
            return $this->json(['error' => 'Укажите identity_token', 'code' => 'missing_identity_token'], 400);
        }

        try {
            $claims = $this->appleAuth->verifyIdentityToken($identityToken);
        } catch (\InvalidArgumentException $e) {
            return $this->json(['error' => $e->getMessage(), 'code' => 'invalid_apple_token'], 401);
        }

        $fullName = $this->composeFullName($data);

        try {
            $user = $this->appleAuth->resolveUser(
                $claims['sub'],
                $claims['email'] ?? null,
                $fullName,
            );
        } catch (\RuntimeException $e) {
            if ($e->getMessage() === 'user_blocked') {
                return $this->json(['error' => 'Access denied', 'code' => 'user_blocked'], 403);
            }
            throw $e;
        }

        return $this->json($this->mobileTokens->issue($user, true));
    }

    /** @param array<string, mixed> $data */
    private function composeFullName(array $data): ?string
    {
        $given = trim((string) ($data['given_name'] ?? $data['givenName'] ?? ''));
        $family = trim((string) ($data['family_name'] ?? $data['familyName'] ?? ''));
        $full = trim($given . ' ' . $family);
        if ($full !== '') {
            return $full;
        }
        $legacy = trim((string) ($data['full_name'] ?? $data['fullName'] ?? ''));

        return $legacy !== '' ? $legacy : null;
    }
}
