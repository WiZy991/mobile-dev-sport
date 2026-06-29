<?php

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Service\Payment\PaymentStatusSyncService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/payments/alfa')]
class PaymentCallbackController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly PaymentStatusSyncService $statusSyncService,
    ) {}

    #[Route('/callback', name: 'api_payments_alfa_callback', methods: ['GET', 'POST'])]
    public function callback(Request $request): Response
    {
        $payload = array_merge(
            $request->query->all(),
            $request->request->all(),
        );

        if ($request->getContent() !== '') {
            $json = json_decode($request->getContent(), true);
            if (is_array($json)) {
                $payload = array_merge($payload, $json);
            }
        }

        $mdOrder = (string) ($payload['mdOrder'] ?? $payload['orderId'] ?? '');
        $orderNumber = (string) ($payload['orderNumber'] ?? '');

        $payment = null;
        if ($mdOrder !== '') {
            $payment = $this->em->getRepository(Payment::class)->findOneBy(['alfaOrderId' => $mdOrder]);
        }
        if ($payment === null && $orderNumber !== '') {
            $payment = $this->em->getRepository(Payment::class)->findOneBy(['orderNumber' => $orderNumber]);
        }

        if ($payment === null) {
            return new Response('payment not found', Response::HTTP_NOT_FOUND);
        }

        $this->statusSyncService->syncFromGateway($payment, $payload);

        return new Response('OK', Response::HTTP_OK);
    }
}
