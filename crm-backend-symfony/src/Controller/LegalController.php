<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\BinaryFileResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpFoundation\ResponseHeaderBag;
use Symfony\Component\Routing\Attribute\Route;

class LegalController extends AbstractController
{
    /** Документы, показываемые в индексе /legal и в навигации. */
    private const INDEX_DOCUMENTS = [
        'license_agreement' => [
            'title' => 'Договор-оферта для Клуба',
            'route' => 'legal_license_agreement',
            'pdf' => 'license_agreement.pdf',
        ],
        'user_agreement' => [
            'title' => 'Пользовательское соглашение мобильного приложения',
            'route' => 'legal_user_agreement',
            'pdf' => 'user_agreement.pdf',
        ],
        'privacy' => [
            'title' => 'Политика по обработке персональных данных',
            'route' => 'legal_privacy',
            'pdf' => 'privacy.pdf',
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
        return $this->servePublicLegalPdf('license_agreement.pdf', 'license_agreement.pdf');
    }

    #[Route('/user_agreement/', name: 'legal_user_agreement', methods: ['GET'])]
    public function userAgreement(): Response
    {
        return $this->servePublicLegalPdf('user_agreement.pdf', 'user_agreement.pdf');
    }

    #[Route('/privacy', name: 'legal_privacy', methods: ['GET'])]
    #[Route('/privacy/', name: 'legal_privacy_slash', methods: ['GET'])]
    public function privacy(): Response
    {
        // Google Play требует HTML-страницу (text/html), а не прямой PDF.
        return $this->render('legal/page.html.twig', [
            'title' => 'Политика обработки и защиты персональных данных',
            'download_url' => $this->generateUrl('legal_privacy_pdf'),
            'download_label' => 'Открыть PDF',
            'content_template' => 'legal/content/privacy-policy-worldcashfit.html.twig',
        ]);
    }

    #[Route('/privacy.pdf', name: 'legal_privacy_pdf', methods: ['GET'])]
    public function privacyPdf(): Response
    {
        return $this->servePublicLegalPdf('privacy.pdf', 'privacy.pdf');
    }

    #[Route('/client-agreement', name: 'legal_client_agreement', methods: ['GET'])]
    public function clientAgreement(): Response
    {
        return $this->redirectToRoute('legal_index', [], Response::HTTP_MOVED_PERMANENTLY);
    }

    #[Route('/trainer-agreement', name: 'legal_trainer_agreement', methods: ['GET'])]
    public function trainerAgreement(): Response
    {
        return $this->redirectToRoute('legal_index', [], Response::HTTP_MOVED_PERMANENTLY);
    }

    /** Старый URL согласия — перенаправляем на consent_user (ООО Ворлдкэшбокс). */
    #[Route('/personal-data-consent', name: 'legal_personal_data_consent', methods: ['GET'])]
    public function personalDataConsent(): Response
    {
        return $this->redirectToRoute('legal_consent_user', [], Response::HTTP_MOVED_PERMANENTLY);
    }

    #[Route('/consent_user', name: 'legal_consent_user', methods: ['GET'])]
    #[Route('/consent_user/', name: 'legal_consent_user_slash', methods: ['GET'])]
    public function consentUser(): Response
    {
        return $this->servePublicLegalPdf('consent_user.pdf', 'consent_user.pdf');
    }

    /** Согласие на обработку ПДн для формы заявки на демо. */
    #[Route('/consent-personal-data', name: 'legal_consent_personal_data', methods: ['GET'])]
    #[Route('/consent-personal-data/', name: 'legal_consent_personal_data_slash', methods: ['GET'])]
    public function consentPersonalData(): Response
    {
        return $this->servePublicLegalPdf('consent-personal-data.pdf', 'consent-personal-data.pdf');
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
        if (!isset(self::INDEX_DOCUMENTS[$slug])) {
            throw $this->createNotFoundException('Документ не найден.');
        }

        $filename = self::INDEX_DOCUMENTS[$slug]['pdf'];

        return $this->servePublicLegalPdf($filename, $filename);
    }

    /**
     * @return list<array{slug: string, title: string, url: string}>
     */
    private function documentsForIndex(): array
    {
        $documents = [];
        foreach (self::INDEX_DOCUMENTS as $slug => $doc) {
            $documents[] = [
                'slug' => $slug,
                'title' => $doc['title'],
                'url' => $this->generateUrl($doc['route']),
            ];
        }

        $documents[] = [
            'slug' => 'requisites',
            'title' => 'Реквизиты',
            'url' => $this->generateUrl('legal_requisites'),
        ];

        return $documents;
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
