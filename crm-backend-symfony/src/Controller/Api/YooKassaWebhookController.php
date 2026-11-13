<?php

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Service\Payment\PaymentStatusSyncService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

/**
 * Уведомления ЮKassa. Тело не доверяем: статус забираем повторным запросом в API.
 */
class YooKassaWebhookController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly PaymentStatusSyncService $statusSyncService,
    ) {}

    #[Route('/api/v1/payments/yookassa/webhook', name: 'api_payments_yookassa_webhook', methods: ['POST'])]
    public function webhook(Request $request): Response
    {
        $payload = json_decode($request->getContent(), true);
        if (!is_array($payload)) {
            return new Response('OK', Response::HTTP_OK);
        }

        $object = $payload['object'] ?? null;
        if (!is_array($object)) {
            return new Response('OK', Response::HTTP_OK);
        }

        $remoteId = trim((string) ($object['id'] ?? ''));
        $metadata = $object['metadata'] ?? [];
        $orderNumber = is_array($metadata) ? trim((string) ($metadata['order_number'] ?? '')) : '';

        $payment = null;
        if ($remoteId !== '') {
            $payment = $this->em->getRepository(Payment::class)->findOneBy(['yookassaPaymentId' => $remoteId]);
        }
        if ($payment === null && $orderNumber !== '') {
            $payment = $this->em->getRepository(Payment::class)->findOneBy(['orderNumber' => $orderNumber]);
        }

        if ($payment instanceof Payment && $payment->getGateway() === Payment::GATEWAY_YOOKASSA) {
            try {
                $this->statusSyncService->syncFromGateway($payment, $payload);
            } catch (\Throwable) {
                return new Response('temporary error', Response::HTTP_SERVICE_UNAVAILABLE);
            }
        }

        return new Response('OK', Response::HTTP_OK);
    }
}
