<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/leads')]
class LeadController extends AbstractController
{
    public function __construct(
        private readonly LeadIngestionService $leadIngestion,
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** Публичная заявка с сайта, виджета или лендинга. */
    #[Route('', name: 'api_leads_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        if ($data === [] && $request->request->count() > 0) {
            $data = $request->request->all();
        }

        if (!empty($data['website'])) {
            return $this->json(['success' => true]);
        }

        $name = trim((string) ($data['name'] ?? ''));
        $phone = trim((string) ($data['phone'] ?? ''));
        $email = trim((string) ($data['email'] ?? ''));
        $comment = trim((string) ($data['comment'] ?? $data['message'] ?? ''));
        $source = trim((string) ($data['source'] ?? LeadSource::SITE));

        if ($name === '' || $phone === '') {
            return $this->json(['error' => 'Укажите имя и телефон'], 400);
        }

        $extra = [];
        foreach (['biz', 'business_type', 'club_id', 'utm_source', 'utm_campaign'] as $key) {
            if (!empty($data[$key])) {
                $extra[] = $key . ': ' . $data[$key];
            }
        }
        if ($extra !== []) {
            $comment = trim($comment . ($comment !== '' ? "\n" : '') . implode('; ', $extra));
        }

        try {
            $lead = $this->leadIngestion->ingest(
                $name,
                $phone,
                $email !== '' ? $email : null,
                LeadSource::isValid($source) ? $source : LeadSource::SITE,
                $comment !== '' ? $comment : null,
            );
            $this->em->flush();
        } catch (\InvalidArgumentException $e) {
            return $this->json(['error' => $e->getMessage()], 400);
        }

        return $this->json([
            'success' => true,
            'id' => $lead->getId(),
        ], 201);
    }
}
