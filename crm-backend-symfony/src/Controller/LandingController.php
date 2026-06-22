<?php

namespace App\Controller;

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
    ) {
    }

    #[Route('/', name: 'landing_page', methods: ['GET'])]
    public function index(): Response
    {
        return $this->render('landing/index.html.twig', [
            'admin_url' => '/admin',
        ]);
    }

    #[Route('/lead', name: 'landing_lead_submit', methods: ['POST'])]
    public function submitLead(Request $request): Response
    {
        if ($request->request->get('website')) {
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
