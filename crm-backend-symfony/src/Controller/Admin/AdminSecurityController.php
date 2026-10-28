<?php

namespace App\Controller\Admin;

use App\Entity\PasswordResetToken;
use App\Service\Auth\AuthAbuseGuard;
use App\Service\Auth\PasswordResetService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Security\Http\Authentication\AuthenticationUtils;

final class AdminSecurityController extends AbstractController
{
    public function __construct(
        private readonly AuthAbuseGuard $abuse,
        private readonly PasswordResetService $passwordReset,
    ) {
    }

    public function login(AuthenticationUtils $authUtils, Request $request): Response
    {
        $session = $request->getSession();
        $captcha = $session->get('admin_login_captcha');
        if (!\is_array($captcha)) {
            $captcha = $this->abuse->challengeIfNeeded($request, 'staff_login');
        } else {
            $session->remove('admin_login_captcha');
        }

        return $this->render('admin/login.html.twig', [
            'last_username' => $authUtils->getLastUsername(),
            'error' => $authUtils->getLastAuthenticationError(),
            'captcha' => \is_array($captcha) ? $captcha : null,
        ]);
    }

    public function forgotPassword(Request $request): Response
    {
        $captcha = $this->abuse->challengeIfNeeded($request, 'password_forgot');
        if ($request->isMethod('POST')) {
            if (!$this->isCsrfTokenValid('admin_forgot', (string) $request->request->get('_csrf_token'))) {
                $this->addFlash('danger', 'Сессия устарела. Обновите страницу и попробуйте снова.');

                return $this->redirectToRoute('admin_forgot_password');
            }
            $webError = $this->abuse->enforceWeb(
                $request,
                'password_forgot',
                trim((string) $request->request->get('captcha_id', '')) ?: null,
                trim((string) $request->request->get('captcha_answer', '')) ?: null,
            );
            if ($webError !== null) {
                $this->addFlash('danger', $webError);

                return $this->render('admin/forgot_password.html.twig', [
                    'captcha' => $this->abuse->challengeIfNeeded($request, 'password_forgot'),
                    'email' => trim((string) $request->request->get('email', '')),
                ]);
            }
            $email = mb_strtolower(trim((string) $request->request->get('email', '')));
            if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
                $this->addFlash('danger', 'Укажите корректный email.');
            } else {
                $this->passwordReset->requestReset(
                    $email,
                    PasswordResetToken::AUDIENCE_STAFF,
                    $request->getSchemeAndHttpHost(),
                    $request->getClientIp(),
                );
                $this->addFlash('success', PasswordResetService::GENERIC_OK);
            }

            return $this->redirectToRoute('admin_forgot_password');
        }

        return $this->render('admin/forgot_password.html.twig', [
            'captcha' => $captcha,
            'email' => '',
        ]);
    }

    public function resetPassword(Request $request): Response
    {
        $token = trim((string) $request->query->get('token', $request->request->get('token', '')));
        $valid = $token !== '' && $this->passwordReset->validateLinkToken($token, PasswordResetToken::AUDIENCE_STAFF);

        if ($request->isMethod('POST')) {
            if (!$this->isCsrfTokenValid('admin_reset', (string) $request->request->get('_csrf_token'))) {
                $this->addFlash('danger', 'Сессия устарела. Обновите страницу и попробуйте снова.');

                return $this->redirectToRoute('admin_reset_password', ['token' => $token]);
            }
            $password = (string) $request->request->get('password', '');
            $confirm = (string) $request->request->get('password_confirm', '');
            if ($password !== $confirm) {
                $this->addFlash('danger', 'Пароли не совпадают.');
            } else {
                $error = $this->passwordReset->resetByLinkToken($token, $password, PasswordResetToken::AUDIENCE_STAFF);
                if ($error !== null) {
                    $this->addFlash('danger', $error);
                } else {
                    $this->addFlash('success', 'Пароль обновлён. Войдите с новым паролем.');

                    return $this->redirectToRoute('admin_login');
                }
            }
            $valid = $this->passwordReset->validateLinkToken($token, PasswordResetToken::AUDIENCE_STAFF);
        }

        return $this->render('admin/reset_password.html.twig', [
            'token' => $token,
            'valid' => $valid,
        ]);
    }

    public function loginCheck(): never
    {
        throw new \LogicException('Должно перехватываться фаерволом входа.');
    }

    public function logout(): never
    {
        throw new \LogicException('Должно перехватываться фаерволом выхода.');
    }
}
