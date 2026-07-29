<?php

namespace App\Controller;

use App\Service\Admin\ClubSettingsStore;
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

    #[Route('/forgot-password', name: 'forgot_password', methods: ['GET'])]
    public function forgotPassword(): Response
    {
        $get = function (string $key, string $default): string {
            return $this->clubSettings->get($key) ?? $default;
        };

        $clubName = $get('name', 'Доброзал');
        if (trim($clubName) === 'FitnessClub') {
            $clubName = 'Доброзал';
        }

        return $this->render('landing/forgot_password.html.twig', [
            'club_name' => $clubName,
            'phone' => $get('phone', '+7 (495) 123-45-67'),
            'email' => $get('email', 'info@fitnessclub.ru'),
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
