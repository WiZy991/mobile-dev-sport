<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Entity\StaffUser;
use App\Service\CurrentStaffUserResolver;
use App\Service\Payment\PaymentStatusSyncService;
use App\Service\Payment\TrainerRentalPaymentInitService;
use App\Service\Staff\StaffClubRentalService;
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
        private readonly StaffClubRentalService $clubRentals,
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
        $clubId = (int) ($data['club_id'] ?? 0);
        $months = (int) ($data['months'] ?? $data['duration_months'] ?? 1);

        $result = $this->rentalInit->init($user, $offerAccepted, $clubId, $months);
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
            'club_id' => $payment->getClub()?->getId(),
            'duration_months' => $payment->getDurationMonths(),
            'duration_days' => StaffClubRentalService::RENTAL_DAYS,
            'expires_at' => $payment->getExpiresAt()?->format(\DateTimeInterface::ATOM),
            'onboarding' => $this->onboarding->serialize($user),
        ], 201);
    }

    #[Route('/active-club', name: 'api_staff_rental_active_club', methods: ['PATCH', 'POST'])]
    public function activeClub(Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $clubId = (int) ($data['club_id'] ?? 0);
        if ($clubId <= 0) {
            return $this->json(['error' => 'Укажите club_id', 'code' => 'club_required'], 400);
        }

        $result = $this->clubRentals->setActiveClub($user, $clubId);
        if (isset($result['error'])) {
            return $this->json($result['error'], $result['status']);
        }

        return $this->json([
            'ok' => true,
            'active_club_id' => $user->getActiveClub()?->getId(),
            'onboarding' => $this->onboarding->serialize($user),
        ]);
    }

    #[Route('/payments', name: 'api_staff_rental_payments', methods: ['GET'])]
    public function payments(Request $request): JsonResponse
    {
        $user = $this->staffResolver->resolve($request);
        if (!$user instanceof StaffUser) {
            return $this->json(['error' => 'Unauthorized', 'code' => 'unauthorized'], 401);
        }

        /** @var list<Payment> $rows */
        $rows = $this->em->createQueryBuilder()
            ->select('p')
            ->from(Payment::class, 'p')
            ->where('p.staffUser = :staff')
            ->andWhere('p.type = :type')
            ->setParameter('staff', $user)
            ->setParameter('type', Payment::TYPE_TRAINER_RENTAL)
            ->orderBy('p.createdAt', 'DESC')
            ->setMaxResults(50)
            ->getQuery()
            ->getResult();

        $items = [];
        foreach ($rows as $payment) {
            $items[] = [
                'id' => $payment->getId(),
                'status' => $payment->getStatus(),
                'amount_kopecks' => $payment->getAmountKopecks(),
                'amount_rub' => round($payment->getAmountKopecks() / 100, 2),
                'club_id' => $payment->getClub()?->getId(),
                'club_name' => $payment->getClub()?->getName(),
                'duration_months' => $payment->getDurationMonths(),
                'duration_days' => StaffClubRentalService::RENTAL_DAYS,
                'paid_at' => $payment->getPaidAt()?->format(\DateTimeInterface::ATOM),
                'created_at' => $payment->getCreatedAt()->format(\DateTimeInterface::ATOM),
                'failure_reason' => $payment->getFailureReason(),
            ];
        }

        return $this->json([
            'items' => $items,
            'onboarding' => $this->onboarding->serialize($user),
        ]);
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
            'club_id' => $payment->getClub()?->getId(),
            'duration_months' => $payment->getDurationMonths(),
            'duration_days' => StaffClubRentalService::RENTAL_DAYS,
            'failure_reason' => $payment->getFailureReason(),
            'onboarding' => $this->onboarding->serialize($user),
        ]);
    }
}
