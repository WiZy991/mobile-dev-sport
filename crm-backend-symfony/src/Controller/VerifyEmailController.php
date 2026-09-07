<?php

declare(strict_types=1);

namespace App\Controller;

use App\Service\Auth\EmailVerificationService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

class VerifyEmailController extends AbstractController
{
    public function __construct(
        private readonly EmailVerificationService $emailVerification,
    ) {
    }

    #[Route('/verify-email', name: 'verify_email', methods: ['GET'])]
    public function __invoke(Request $request): Response
    {
        $token = (string) $request->query->get('token', '');
        $ok = $this->emailVerification->confirmByToken($token);

        return $this->render('legal/verify_email.html.twig', [
            'ok' => $ok,
        ]);
    }
}
