<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Attribute\Route;

class LegalController extends AbstractController
{
    private const DOCUMENTS = [
        'license_agreement' => [
            'title' => 'Договор-оферта на использование WorldCashFit',
            'content' => 'legal/content/offer.html.twig',
            'download' => 'offer.docx',
        ],
        'privacy' => [
            'title' => 'Политика конфиденциальности',
            'content' => 'legal/content/privacy-policy.html.twig',
            'download' => 'privacy-policy.docx',
        ],
        'client-agreement' => [
            'title' => 'Договор с клиентом',
            'content' => 'legal/content/client-agreement.html.twig',
            'download' => 'client-agreement.docx',
        ],
        'trainer-agreement' => [
            'title' => 'Договор с тренером',
            'content' => 'legal/content/trainer-agreement.html.twig',
            'download' => 'trainer-agreement.docx',
        ],
        'personal-data-consent' => [
            'title' => 'Согласие на обработку персональных данных',
            'content' => 'legal/content/personal-data-consent.html.twig',
            'download' => 'personal-data-consent.docx',
        ],
    ];

    #[Route('/legal', name: 'legal_index', methods: ['GET'])]
    public function index(): Response
    {
        return $this->render('legal/index.html.twig', [
            'documents' => $this->documentsForIndex(),
        ]);
    }

    #[Route('/license_agreement/', name: 'legal_license_agreement', methods: ['GET'])]
    #[Route('/terms', name: 'legal_terms', methods: ['GET'])]
    public function licenseAgreement(): Response
    {
        return $this->renderDocument('license_agreement');
    }

    #[Route('/privacy', name: 'legal_privacy', methods: ['GET'])]
    public function privacy(): Response
    {
        return $this->renderDocument('privacy');
    }

    #[Route('/client-agreement', name: 'legal_client_agreement', methods: ['GET'])]
    public function clientAgreement(): Response
    {
        return $this->renderDocument('client-agreement');
    }

    #[Route('/trainer-agreement', name: 'legal_trainer_agreement', methods: ['GET'])]
    public function trainerAgreement(): Response
    {
        return $this->renderDocument('trainer-agreement');
    }

    #[Route('/personal-data-consent', name: 'legal_personal_data_consent', methods: ['GET'])]
    public function personalDataConsent(): Response
    {
        return $this->renderDocument('personal-data-consent');
    }

    #[Route('/legal/{slug}/download', name: 'legal_download', methods: ['GET'])]
    public function download(string $slug): Response
    {
        if (!isset(self::DOCUMENTS[$slug])) {
            throw $this->createNotFoundException('Документ не найден.');
        }

        $filename = self::DOCUMENTS[$slug]['download'];
        $path = $this->getParameter('kernel.project_dir') . '/public/legal/' . $filename;

        if (!is_file($path)) {
            throw $this->createNotFoundException('Файл документа не найден.');
        }

        $response = new BinaryFileResponse($path);
        $response->setContentDisposition(
            ResponseHeaderBag::DISPOSITION_ATTACHMENT,
            $filename
        );

        return $response;
    }

    private function renderDocument(string $slug): Response
    {
        if (!isset(self::DOCUMENTS[$slug])) {
            throw $this->createNotFoundException('Документ не найден.');
        }

        $doc = self::DOCUMENTS[$slug];

        return $this->render('legal/page.html.twig', [
            'title' => $doc['title'],
            'content_template' => $doc['content'],
            'download_url' => $this->generateUrl('legal_download', ['slug' => $slug]),
            'documents' => $this->documentsForIndex(),
        ]);
    }

    /**
     * @return list<array{slug: string, title: string, url: string}>
     */
    private function documentsForIndex(): array
    {
        $routeMap = [
            'license_agreement' => 'legal_license_agreement',
            'privacy' => 'legal_privacy',
            'client-agreement' => 'legal_client_agreement',
            'trainer-agreement' => 'legal_trainer_agreement',
            'personal-data-consent' => 'legal_personal_data_consent',
        ];

        $documents = [];
        foreach ($routeMap as $slug => $route) {
            $documents[] = [
                'slug' => $slug,
                'title' => self::DOCUMENTS[$slug]['title'],
                'url' => $this->generateUrl($route),
            ];
        }

        return $documents;
    }
}
