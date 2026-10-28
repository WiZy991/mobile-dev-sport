<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\PasswordResetToken;
use App\Service\Auth\AuthAbuseGuard;
use App\Service\Auth\PasswordResetService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/v1/auth')]
class PasswordResetController extends AbstractController
{
    public function __construct(
        private readonly PasswordResetService $passwordReset,
        private readonly AuthAbuseGuard $abuse,
    ) {
    }

    #[Route('/password/forgot', name: 'api_auth_password_forgot', methods: ['POST'])]
    public function forgot(Request $request): JsonResponse
    {
        $blocked = $this->abuse->enforceFromRequest($request, 'password_forgot');
        if ($blocked !== null) {
            return $blocked;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Укажите корректный email', 'code' => 'invalid_email'], 400);
        }

        $this->passwordReset->requestReset(
            $email,
            PasswordResetToken::AUDIENCE_CLIENT,
            $request->getSchemeAndHttpHost(),
            $request->getClientIp(),
        );

        return $this->json([
            'ok' => true,
            'message' => PasswordResetService::GENERIC_OK,
        ]);
    }

    #[Route('/password/reset', name: 'api_auth_password_reset', methods: ['POST'])]
    public function reset(Request $request): JsonResponse
    {
        $blocked = $this->abuse->enforceFromRequest($request, 'password_forgot');
        if ($blocked !== null) {
            return $blocked;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $code = (string) ($data['code'] ?? '');
        $password = (string) ($data['password'] ?? $data['new_password'] ?? '');

        $error = $this->passwordReset->resetByCode(
            $email,
            $code,
            $password,
            PasswordResetToken::AUDIENCE_CLIENT,
        );
        if ($error !== null) {
            return $this->json(['error' => $error, 'code' => 'reset_failed'], 400);
        }

        return $this->json([
            'ok' => true,
            'message' => 'Пароль обновлён. Войдите с новым паролем.',
        ]);
    }
}
