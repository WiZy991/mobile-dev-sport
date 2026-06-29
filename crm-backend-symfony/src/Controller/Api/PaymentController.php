<?php

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Service\CurrentUserResolver;
use App\Service\Payment\PaymentStatusSyncService;
use App\Service\Payment\SubscriptionPaymentInitService;
use App\Service\Payment\SubscriptionPurchaseQuoteService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/payments')]
class PaymentController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly SubscriptionPurchaseQuoteService $quoteService,
        private readonly SubscriptionPaymentInitService $initService,
        private readonly PaymentStatusSyncService $statusSyncService,
    ) {}

    private function requireUser(Request $request): JsonResponse|array
    {
        $auth = $this->userResolver->resolveAuthState($request);
        if ($auth['user'] === null) {
            $status = ($auth['code'] ?? '') === 'user_blocked' ? 403 : 401;

            return $this->json([
                'error' => ($auth['code'] ?? '') === 'user_blocked' ? 'Access denied' : 'Unauthorized',
                'code' => $auth['code'] ?? 'unauthorized',
                'message' => $auth['message'] ?? 'Войдите в приложение, чтобы продолжить.',
            ], $status);
        }

        return $auth;
    }

    #[Route('/promo/validate', name: 'api_payments_promo_validate', methods: ['POST'])]
    public function validatePromo(Request $request): JsonResponse
    {
        $auth = $this->requireUser($request);
        if ($auth instanceof JsonResponse) {
            return $auth;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $promoCodeRaw = trim((string) ($data['promo_code'] ?? $data['promoCode'] ?? ''));

        if ($promoCodeRaw === '') {
            return $this->json(['promo_valid' => false, 'promo_error' => 'Введите промокод']);
        }

        $promo = $this->em->getRepository(\App\Entity\PromoCode::class)->findOneBy([
            'code' => strtoupper($promoCodeRaw),
        ]);

        if ($promo === null || !$promo->isValid()) {
            return $this->json([
                'promo_valid' => false,
                'promo_error' => 'Промокод недействителен',
            ]);
        }

        return $this->json([
            'promo_valid' => true,
            'promo_code' => $promo->getCode(),
            'discount_percent' => $promo->getDiscountPercent(),
            'discount_amount' => $promo->getDiscountAmount(),
        ]);
    }

    #[Route('/subscription/quote', name: 'api_payments_subscription_quote', methods: ['POST'])]
    public function quote(Request $request): JsonResponse
    {
        $auth = $this->requireUser($request);
        if ($auth instanceof JsonResponse) {
            return $auth;
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $planIdRaw = (string) ($data['plan_id'] ?? $data['planId'] ?? '');
        $promoCodeRaw = trim((string) ($data['promo_code'] ?? $data['promoCode'] ?? ''));

        $plan = $this->quoteService->findPlan($planIdRaw);
        if (!$plan) {
            return $this->json(['error' => 'Plan not found'], 404);
        }

        $quote = $this->quoteService->quote($plan, $promoCodeRaw);

        $response = [
            'plan_id' => 'plan-' . $plan->getId(),
            'original_price' => $quote->originalPrice,
            'final_price' => $quote->finalPrice,
            'discount_amount' => $quote->discountAmount,
            'amount_kopecks' => $quote->amountKopecks,
            'promo_valid' => $promoCodeRaw !== '' && $quote->promo !== null,
        ];

        if ($promoCodeRaw !== '' && $quote->promo === null) {
            $response['promo_error'] = 'Промокод недействителен';
        }

        return $this->json($response);
    }

    #[Route('/subscription/init', name: 'api_payments_subscription_init', methods: ['POST'])]
    public function initSubscriptionPayment(Request $request): JsonResponse
    {
        $auth = $this->requireUser($request);
        if ($auth instanceof JsonResponse) {
            return $auth;
        }
        $user = $auth['user'];

        $data = json_decode($request->getContent(), true) ?? [];
        $planIdRaw = (string) ($data['plan_id'] ?? $data['planId'] ?? '');
        $promoCodeRaw = trim((string) ($data['promo_code'] ?? $data['promoCode'] ?? ''));

        $plan = $this->quoteService->findPlan($planIdRaw);
        if (!$plan) {
            return $this->json(['error' => 'Plan not found'], 404);
        }

        $result = $this->initService->init($user, $plan, $promoCodeRaw);
        if (isset($result['error'])) {
            return $this->json($result['error'], $result['status']);
        }

        /** @var Payment $payment */
        $payment = $result['payment'];

        return $this->json($this->serializePayment($payment), 201);
    }

    #[Route('/{id}/status', name: 'api_payments_status', methods: ['GET'], requirements: ['id' => '\d+'])]
    public function status(int $id, Request $request): JsonResponse
    {
        $auth = $this->requireUser($request);
        if ($auth instanceof JsonResponse) {
            return $auth;
        }
        $user = $auth['user'];

        $payment = $this->em->getRepository(Payment::class)->find($id);
        if (!$payment) {
            return $this->json(['error' => 'Payment not found'], 404);
        }
        if ($payment->getUser()->getId() !== $user->getId()) {
            return $this->json(['error' => 'Forbidden'], 403);
        }

        if ($payment->isPending() && $payment->getAlfaOrderId() !== null) {
            $this->statusSyncService->syncFromGateway($payment);
            $this->em->refresh($payment);
        }

        return $this->json($this->serializePayment($payment, includeSubscription: true));
    }

    private function serializePayment(Payment $payment, bool $includeSubscription = false): array
    {
        $plan = $payment->getSubscriptionPlan();
        $data = [
            'payment_id' => $payment->getId(),
            'status' => $payment->getStatus(),
            'payment_url' => $payment->getPaymentUrl(),
            'amount' => $payment->getAmountKopecks() / 100,
            'amount_kopecks' => $payment->getAmountKopecks(),
            'currency' => $payment->getCurrency(),
            'order_number' => $payment->getOrderNumber(),
            'plan_id' => 'plan-' . $plan->getId(),
            'plan_name' => $plan->getName(),
            'discount_amount' => $payment->getDiscountAmount(),
            'original_price' => $plan->getPrice(),
            'final_price' => $payment->getAmountKopecks() / 100,
            'expires_at' => $payment->getExpiresAt()?->format(\DateTimeInterface::ATOM),
            'paid_at' => $payment->getPaidAt()?->format(\DateTimeInterface::ATOM),
            'failure_reason' => $payment->getFailureReason(),
            'payment_way' => $payment->getPaymentWay(),
        ];

        if ($includeSubscription && $payment->getSubscription() !== null) {
            $sub = $payment->getSubscription();
            $data['subscription'] = [
                'id' => 'sub-' . $sub->getId(),
                'name' => $plan->getName(),
                'status' => $sub->getStatus(),
                'start_date' => $sub->getStartDate()->format('Y-m-d'),
                'end_date' => $sub->getEndDate()?->format('Y-m-d'),
            ];
        }

        return $data;
    }
}
