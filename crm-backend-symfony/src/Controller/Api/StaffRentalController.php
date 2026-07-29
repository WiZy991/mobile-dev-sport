<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Entity\StaffUser;
use App\Service\CurrentStaffUserResolver;
use App\Service\Payment\PaymentStatusSyncService;
use App\Service\Payment\TrainerRentalPaymentInitService;
use App\Service\Staff\StaffOnboardingService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff/rental')]
final class StaffRentalController extends AbstractController
{
    public function __construct(
        private readonly CurrentStaffUserResolver $staffResolver,
        private readonly StaffOnboardingService $onboarding,
        private readonly TrainerRentalPaymentInitService $rentalInit,
        private readonly PaymentStatusSyncService $statusSync,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('/quote', name: 'api_staff_rental_quote', methods: ['GET', 'POST'])]
    public function quote(Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        return $this->json($this->onboarding->serialize($user));
    }

    #[Route('/init', name: 'api_staff_rental_init', methods: ['POST'])]
    public function init(Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $offerAccepted = (bool) ($data['offer_accepted'] ?? false);

        $result = $this->rentalInit->init($user, $offerAccepted);
        if (isset($result['error'])) {
            return $this->json($result['error'], $result['status']);
        }

        /** @var Payment $payment */
        $payment = $result['payment'];

        return $this->json([
            'payment_id' => $payment->getId(),
            'status' => $payment->getStatus(),
            'payment_url' => $payment->getPaymentUrl(),
            'amount' => $payment->getAmountKopecks() / 100,
            'amount_kopecks' => $payment->getAmountKopecks(),
            'expires_at' => $payment->getExpiresAt()?->format(\DateTimeInterface::ATOM),
            'onboarding' => $this->onboarding->serialize($user),
        ], 201);
    }

    #[Route('/payments/{id}/status', name: 'api_staff_rental_payment_status', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function status(int $id, Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $payment = $this->em->getRepository(Payment::class)->find($id);
        if (!$payment || $payment->getType() !== Payment::TYPE_TRAINER_RENTAL) {
            return $this->json(['error' => 'Payment not found'], 404);
        }
        if ($payment->getStaffUser()?->getId() !== $user->getId()) {
            return $this->json(['error' => 'Forbidden'], 403);
        }

        if ($payment->isPending() && $payment->getAlfaOrderId() !== null) {
            try {
                $this->statusSync->syncFromGateway($payment);
                $this->em->refresh($payment);
                $this->em->refresh($user);
            } catch (\Throwable) {
            }
        }

        return $this->json([
            'payment_id' => $payment->getId(),
            'status' => $payment->getStatus(),
            'payment_url' => $payment->getPaymentUrl(),
            'amount_kopecks' => $payment->getAmountKopecks(),
            'failure_reason' => $payment->getFailureReason(),
            'onboarding' => $this->onboarding->serialize($user),
        ]);
    }
}
