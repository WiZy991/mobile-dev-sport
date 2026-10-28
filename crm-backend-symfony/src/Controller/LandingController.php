<?php

namespace App\Controller;

use App\Entity\PasswordResetToken;
use App\Service\Admin\ClubSettingsStore;
use App\Service\Auth\AuthAbuseGuard;
use App\Service\Auth\PasswordResetService;
use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class LandingController extends AbstractController
{
    public function __construct(
        private readonly LeadIngestionService $leadIngestion,
        private readonly EntityManagerInterface $em,
        private readonly ClubSettingsStore $clubSettings,
        private readonly PasswordResetService $passwordReset,
        private readonly AuthAbuseGuard $abuse,
    ) {
    }

    #[Route('/', name: 'landing_page', methods: ['GET'])]
    public function index(Request $request): Response
    {
        if ($this->isDobrozalHost($request)) {
            return $this->renderDobrozalLanding();
        }

        return $this->render('landing/index.html.twig', [
            'admin_url' => '/admin',
        ]);
    }

    private function isDobrozalHost(Request $request): bool
    {
        $host = strtolower($request->getHost());
        if (str_contains($host, 'dobrozal.ru')) {
            return true;
        }

        return $request->query->getBoolean('dobrozal');
    }

    private function renderDobrozalLanding(): Response
    {
        $get = function (string $key, string $default): string {
            return $this->clubSettings->get($key) ?? $default;
        };

        $clubName = $get('name', 'Доброзал');
        if (trim($clubName) === 'FitnessClub') {
            $clubName = 'Доброзал';
        }

        return $this->render('landing/dobrozal.html.twig', [
            'club_name' => $clubName,
            'club_phone' => $get('contact_phone', '') ?: $get('phone', '+7 (495) 123-45-67'),
            'club_email' => $get('contact_email', '') ?: $get('email', 'info@fitnessclub.ru'),
            'club_address' => $get('address', ''),
        ]);
    }

    #[Route('/forgot-password', name: 'forgot_password', methods: ['GET', 'POST'])]
    public function forgotPassword(Request $request): Response
    {
        $get = function (string $key, string $default): string {
            return $this->clubSettings->get($key) ?? $default;
        };

        $clubName = $get('name', 'Доброзал');
        if (trim($clubName) === 'FitnessClub') {
            $clubName = 'Доброзал';
        }

        $linkToken = trim((string) $request->query->get('token', $request->request->get('token', '')));
        $captcha = $this->abuse->challengeIfNeeded($request, 'password_forgot');

        if ($request->isMethod('POST') && $request->request->get('action') === 'request') {
            $webError = $this->abuse->enforceWeb(
                $request,
                'password_forgot',
                trim((string) $request->request->get('captcha_id', '')) ?: null,
                trim((string) $request->request->get('captcha_answer', '')) ?: null,
            );
            if ($webError !== null) {
                $this->addFlash('danger', $webError);
            } else {
                $email = mb_strtolower(trim((string) $request->request->get('email', '')));
                if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
                    $this->addFlash('danger', 'Укажите корректный email.');
                } else {
                    $this->passwordReset->requestReset(
                        $email,
                        PasswordResetToken::AUDIENCE_CLIENT,
                        $request->getSchemeAndHttpHost(),
                        $request->getClientIp(),
                    );
                    $this->addFlash('success', PasswordResetService::GENERIC_OK);
                }
            }

            return $this->redirectToRoute('forgot_password');
        }

        if ($request->isMethod('POST') && $request->request->get('action') === 'reset') {
            $password = (string) $request->request->get('password', '');
            $confirm = (string) $request->request->get('password_confirm', '');
            if ($password !== $confirm) {
                $this->addFlash('danger', 'Пароли не совпадают.');
            } else {
                $error = $this->passwordReset->resetByLinkToken(
                    $linkToken,
                    $password,
                    PasswordResetToken::AUDIENCE_CLIENT,
                );
                if ($error !== null) {
                    $this->addFlash('danger', $error);
                } else {
                    $this->addFlash('success', 'Пароль обновлён. Войдите в приложении с новым паролем.');

                    return $this->redirectToRoute('forgot_password');
                }
            }
        }

        return $this->render('landing/forgot_password.html.twig', [
            'club_name' => $clubName,
            'phone' => $get('phone', '+7 (495) 123-45-67'),
            'email' => $get('email', 'info@fitnessclub.ru'),
            'captcha' => $captcha,
            'link_token' => $linkToken,
            'link_valid' => $linkToken !== '' && $this->passwordReset->validateLinkToken(
                $linkToken,
                PasswordResetToken::AUDIENCE_CLIENT,
            ),
        ]);
    }

    #[Route('/lead', name: 'landing_lead_submit', methods: ['POST'])]
    public function submitLead(Request $request): Response
    {
        if ($request->request->get('website')) {
            return $this->redirectToRoute('landing_page', ['_fragment' => 'contact']);
        }

        if (!$request->request->get('pd_consent')) {
            $this->addFlash('danger', 'Необходимо дать согласие на обработку персональных данных.');

            return $this->redirectToRoute('landing_page', ['_fragment' => 'contact']);
        }

        $name = trim((string) $request->request->get('name', ''));
        $phone = trim((string) $request->request->get('phone', ''));
        $email = trim((string) $request->request->get('email', ''));
        $comment = trim((string) $request->request->get('comment', ''));
        $biz = trim((string) $request->request->get('biz', ''));

        if ($biz !== '') {
            $comment = trim($comment . ($comment !== '' ? "\n" : '') . 'Тип бизнеса: ' . $biz);
        }

        try {
            $this->leadIngestion->ingest(
                $name,
                $phone,
                $email !== '' ? $email : null,
                LeadSource::SITE,
                $comment !== '' ? $comment : 'Заявка с лендинга',
            );
            $this->em->flush();
            $this->addFlash('success', 'Спасибо! Мы свяжемся с вами в ближайшее время.');
        } catch (\InvalidArgumentException) {
            $this->addFlash('danger', 'Укажите имя и телефон.');
        }

        return $this->redirectToRoute('landing_page', ['_fragment' => 'contact']);
    }
}
