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
            'download' => 'license_agreement.pdf',
            'fallback_download' => 'offer.docx',
        ],
        'user_agreement' => [
            'title' => 'Пользовательское соглашение',
            'content' => 'legal/content/offer.html.twig',
            'download' => 'user_agreement.pdf',
            'fallback_download' => 'offer.docx',
        ],
        'privacy' => [
            'title' => 'Политика по обработке персональных данных',
            'content' => 'legal/content/privacy-policy.html.twig',
            'download' => 'privacy.pdf',
            'fallback_download' => 'privacy-policy.docx',
        ],
        'client-agreement' => [
            'title' => 'Договор с клиентом',
            'content' => 'legal/content/client-agreement.html.twig',
            'download' => 'dobrozal_offer.pdf',
        ],
        'trainer-agreement' => [
            'title' => 'Договор с тренером',
            'content' => 'legal/content/trainer-agreement.html.twig',
            'download' => 'dobrozal_offer.pdf',
        ],
        'personal-data-consent' => [
            'title' => 'Согласие на обработку персональных данных',
            'content' => 'legal/content/personal-data-consent.html.twig',
            'download' => 'consent_user.pdf',
            'fallback_download' => 'personal-data-consent.docx',
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

    #[Route('/user_agreement/', name: 'legal_user_agreement', methods: ['GET'])]
    public function userAgreement(): Response
    {
        return $this->renderDocument('user_agreement');
    }

    #[Route('/privacy', name: 'legal_privacy', methods: ['GET'])]
    #[Route('/privacy/', name: 'legal_privacy_slash', methods: ['GET'])]
    public function privacy(): Response
    {
        return $this->renderDocument('privacy');
    }

    #[Route('/client-agreement', name: 'legal_client_agreement', methods: ['GET'])]
    public function clientAgreement(): Response
    {
        return $this->servePublicLegalPdf('dobrozal_offer.pdf', 'dobrozal_offer.pdf');
    }

    #[Route('/trainer-agreement', name: 'legal_trainer_agreement', methods: ['GET'])]
    public function trainerAgreement(): Response
    {
        return $this->servePublicLegalPdf('dobrozal_offer.pdf', 'dobrozal_offer.pdf');
    }

    #[Route('/personal-data-consent', name: 'legal_personal_data_consent', methods: ['GET'])]
    public function personalDataConsent(): Response
    {
        return $this->servePublicLegalPdf('consent_user.pdf', 'consent_user.pdf');
    }

    #[Route('/consent_user', name: 'legal_consent_user', methods: ['GET'])]
    #[Route('/consent_user/', name: 'legal_consent_user_slash', methods: ['GET'])]
    public function consentUser(): Response
    {
        return $this->servePublicLegalPdf('consent_user.pdf', 'consent_user.pdf');
    }

    #[Route('/doc', name: 'legal_dobrozal_doc', methods: ['GET'])]
    #[Route('/doc/', name: 'legal_dobrozal_doc_slash', methods: ['GET'])]
    public function dobrozalDocuments(): Response
    {
        return $this->render('legal/dobrozal_doc.html.twig');
    }

    #[Route('/doc/offer', name: 'legal_dobrozal_offer_pdf', methods: ['GET'])]
    public function dobrozalOfferPdf(): Response
    {
        return $this->servePublicLegalPdf('dobrozal_offer.pdf', 'dobrozal_offer.pdf');
    }

    #[Route('/doc/privacy', name: 'legal_dobrozal_privacy_pdf', methods: ['GET'])]
    public function dobrozalPrivacyPdf(): Response
    {
        return $this->servePublicLegalPdf('dobrozal_privacy.pdf', 'dobrozal_privacy.pdf');
    }

    #[Route('/requisites', name: 'legal_requisites', methods: ['GET'])]
    public function requisites(): Response
    {
        return $this->render('legal/requisites.html.twig');
    }

    #[Route('/legal/{slug}/download', name: 'legal_download', methods: ['GET'])]
    public function download(string $slug): Response
    {
        if (!isset(self::DOCUMENTS[$slug])) {
            throw $this->createNotFoundException('Документ не найден.');
        }

        $filename = $this->resolveDownloadFilename($slug);
        $path = $this->getParameter('kernel.project_dir') . '/public/legal/' . $filename;

        if (!is_file($path)) {
            throw $this->createNotFoundException('Файл документа не найден.');
        }

        $response = new BinaryFileResponse($path);
        $disposition = str_ends_with(strtolower($filename), '.pdf')
            ? ResponseHeaderBag::DISPOSITION_INLINE
            : ResponseHeaderBag::DISPOSITION_ATTACHMENT;
        $response->setContentDisposition(
            $disposition,
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
            'download_label' => $this->downloadLabel($slug),
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
            'user_agreement' => 'legal_user_agreement',
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

        $documents[] = [
            'slug' => 'requisites',
            'title' => 'Реквизиты',
            'url' => $this->generateUrl('legal_requisites'),
        ];

        return $documents;
    }

    private function resolveDownloadFilename(string $slug): string
    {
        $doc = self::DOCUMENTS[$slug];
        $primary = $doc['download'];
        $primaryPath = $this->getParameter('kernel.project_dir') . '/public/legal/' . $primary;
        if (is_file($primaryPath)) {
            return $primary;
        }

        $fallback = $doc['fallback_download'] ?? null;
        if (is_string($fallback) && $fallback !== '') {
            $fallbackPath = $this->getParameter('kernel.project_dir') . '/public/legal/' . $fallback;
            if (is_file($fallbackPath)) {
                return $fallback;
            }
        }

        return $primary;
    }

    private function downloadLabel(string $slug): string
    {
        $filename = $this->resolveDownloadFilename($slug);

        return str_ends_with(strtolower($filename), '.pdf') ? 'Открыть PDF' : 'Скачать DOCX';
    }

    private function servePublicLegalPdf(string $filename, string $downloadName): Response
    {
        $path = $this->getParameter('kernel.project_dir') . '/public/legal/' . $filename;
        if (!is_file($path)) {
            throw $this->createNotFoundException('Файл документа не найден.');
        }

        $response = new BinaryFileResponse($path);
        $response->setContentDisposition(ResponseHeaderBag::DISPOSITION_INLINE, $downloadName);

        return $response;
    }
}
