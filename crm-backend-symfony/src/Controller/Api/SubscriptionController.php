<?php

namespace App\Controller\Api;

use App\Entity\Club;
use App\Entity\PromoCode;
use App\Entity\Sale;
use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
use App\Service\Api\SberMobileAuthService;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/subscriptions')]
class SubscriptionController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly SberMobileAuthService $sberMobileAuth,
        private readonly bool $requireSberVerificationBeforePurchase = false,
    ) {}

    #[Route('', name: 'api_subscriptions_list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json([]);
        }

        $subs = $this->em->getRepository(Subscription::class)->findBy(['user' => $user]);

        $data = array_map(static function (Subscription $s) {
            $plan = $s->getPlan();
            return [
                'id' => 'sub-' . $s->getId(),
                'name' => $plan->getName(),
                'description' => $plan->getDescription(),
                'type' => $plan->getType(),
                'start_date' => $s->getStartDate()->format('Y-m-d'),
                'end_date' => $s->getEndDate()?->format('Y-m-d'),
                'status' => $s->getStatus(),
                'visits_total' => $s->getVisitsTotal(),
                'visits_used' => $s->getVisitsUsed(),
                'freeze_days_total' => $s->getFreezeDaysTotal(),
                'freeze_days_used' => $s->getFreezeDaysUsed(),
                'is_frozen' => $s->getStatus() === 'frozen',
                'price' => $plan->getPrice(),
                'club_id' => $s->getClub()?->getId(),
                'club_name' => $s->getClub()?->getName(),
            ];
        }, $subs);

        return $this->json($data);
    }

    #[Route('/purchase', name: 'api_subscriptions_purchase', methods: ['POST'])]
    public function purchase(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $planIdRaw = $data['plan_id'] ?? $data['planId'] ?? '';
        $promoCodeRaw = trim((string) ($data['promo_code'] ?? $data['promoCode'] ?? ''));

        $planId = null;
        if (str_starts_with($planIdRaw, 'plan-')) {
            $planId = (int) substr($planIdRaw, 5);
        } else {
            $planId = (int) $planIdRaw;
        }

        /** @var SubscriptionPlan|null $plan */
        $plan = $this->em->getRepository(SubscriptionPlan::class)->find($planId);
        if (!$plan) {
            return $this->json(['error' => 'Plan not found'], 404);
        }

        if ($this->requireSberVerificationBeforePurchase) {
            if (!$this->sberMobileAuth->isReady()) {
                return $this->json([
                    'code' => 'sber_not_configured',
                    'error' => 'Включена обязательная верификация, но Сбер ID для приложения не настроен (SBER_ID_*, SBER_ID_MOBILE_REDIRECT_URI).',
                ], 503);
            }
            if ($user->getPassportVerificationStatus() !== 'verified') {
                try {
                    $authorizeUrl = $this->sberMobileAuth->buildAuthorizeUrlForUser($user);
                } catch (\Throwable $e) {
                    return $this->json([
                        'code' => 'sber_url_failed',
                        'error' => $e->getMessage(),
                    ], 500);
                }

                return $this->json([
                    'code' => 'verification_required',
                    'message' => 'Требуется верификация через Сбер ID. После успеха вернитесь в приложение и нажмите «Купить» снова.',
                    'authorize_url' => $authorizeUrl,
                ], 403);
            }
        }

        $price = $plan->getPrice();
        $discountAmount = 0.0;
        $promo = null;

        if ($promoCodeRaw !== '') {
            $promo = $this->em->getRepository(PromoCode::class)->findOneBy(['code' => strtoupper($promoCodeRaw)]);
            if ($promo && $promo->isValid()) {
                if ($promo->getDiscountPercent() !== null) {
                    $discountAmount = round($price * $promo->getDiscountPercent() / 100, 2);
                } elseif ($promo->getDiscountAmount() !== null) {
                    $discountAmount = min($promo->getDiscountAmount(), $price);
                }
                $price = max(0, $price - $discountAmount);
                $promo->incrementUsedCount();
            } else {
                $promo = null;
                $discountAmount = 0;
            }
        }

        $sub = new Subscription();
        $sub->setUser($user)
            ->setPlan($plan)
            ->setStatus('active')
            ->setVisitsUsed(0);

        $start = new \DateTimeImmutable();
        $sub->setStartDate($start);

        if ($plan->getDurationDays()) {
            $end = $start->modify('+' . $plan->getDurationDays() . ' days');
            $sub->setEndDate($end);
        }
        if ($plan->getVisitsCount()) {
            $sub->setVisitsTotal($plan->getVisitsCount());
        }
        if ($plan->getType() === 'personal') {
            $sub->setFreezeDaysTotal(0);
            $sub->setFreezeDaysUsed(0);
        } else {
            $sub->setFreezeDaysTotal(14);
            $sub->setFreezeDaysUsed(0);
        }
        if ($promo) {
            $sub->setPromoCode($promo);
        }

        $issueClub = $user->getClub();
        if ($issueClub === null) {
            $clubRepo = $this->em->getRepository(Club::class);
            if ((int) $clubRepo->count([]) === 1) {
                $issueClub = $clubRepo->findOneBy([]);
            }
        }
        $sub->setClub($issueClub);

        $this->em->persist($sub);
        $this->em->flush();

        // Создаём продажу для отображения в CRM
        $sale = (new Sale())
            ->setUser($user)
            ->setClientName($user->getName())
            ->setProductName('Абонемент: ' . $plan->getName())
            ->setQuantity(1)
            ->setPrice($price)
            ->setTotal($price)
            ->setPaymentMethod('app_acquiring_stub') // после подключения эквайринга — webhook подтверждения оплаты
            ->setSubscription($sub);
        if ($promo) {
            $sale->setPromoCode($promo);
            $sale->setDiscountAmount($discountAmount);
        }
        $this->em->persist($sale);
        $this->em->flush();

        $result = $this->serializeSubscription($sub);
        $result['final_price'] = $price;
        if ($discountAmount > 0) {
            $result['discount_amount'] = $discountAmount;
            $result['original_price'] = $plan->getPrice();
        }
        return $this->json($result, 201);
    }

    #[Route('/plans', name: 'api_subscriptions_plans', methods: ['GET'])]
    public function plans(): JsonResponse
    {
        $plans = $this->em->getRepository(SubscriptionPlan::class)->findAll();

        $data = array_map(static function (SubscriptionPlan $p) {
            return [
                'id' => 'plan-' . $p->getId(),
                'name' => $p->getName(),
                'description' => $p->getDescription(),
                'price' => $p->getPrice(),
                'duration_days' => $p->getDurationDays(),
                'visits_count' => $p->getVisitsCount(),
                'type' => $p->getType(),
                'features' => [], // можно заполнить позже
                'is_popular' => $p->isPopular(),
            ];
        }, $plans);

        return $this->json($data);
    }

    #[Route('/{id}/freeze', name: 'api_subscriptions_freeze', methods: ['POST'])]
    public function freeze(string $id, Request $request): JsonResponse
    {
        $sub = $this->findSubscriptionByApiId($id);
        if (!$sub) {
            return $this->json(['error' => 'Subscription not found'], 404);
        }

        $days = (int) $request->query->get('days', 0);

        $used = ($sub->getFreezeDaysUsed() ?? 0) + $days;
        $sub->setFreezeDaysUsed($used);
        $sub->setStatus('frozen');

        $this->em->flush();

        return $this->json($this->serializeSubscription($sub));
    }

    #[Route('/{id}/unfreeze', name: 'api_subscriptions_unfreeze', methods: ['POST'])]
    public function unfreeze(string $id): JsonResponse
    {
        $sub = $this->findSubscriptionByApiId($id);
        if (!$sub) {
            return $this->json(['error' => 'Subscription not found'], 404);
        }

        $sub->setStatus('active');
        $this->em->flush();

        return $this->json($this->serializeSubscription($sub));
    }

    private function findSubscriptionByApiId(string $apiId): ?Subscription
    {
        // mobile присылает sub-1, вырежем префикс
        if (str_starts_with($apiId, 'sub-')) {
            $id = (int) substr($apiId, 4);
        } else {
            $id = (int) $apiId;
        }

        return $this->em->getRepository(Subscription::class)->find($id);
    }

    private function serializeSubscription(Subscription $s): array
    {
        $plan = $s->getPlan();
        return [
            'id' => 'sub-' . $s->getId(),
            'name' => $plan->getName(),
            'description' => $plan->getDescription(),
            'type' => $plan->getType(),
            'start_date' => $s->getStartDate()->format('Y-m-d'),
            'end_date' => $s->getEndDate()?->format('Y-m-d'),
            'status' => $s->getStatus(),
            'visits_total' => $s->getVisitsTotal(),
            'visits_used' => $s->getVisitsUsed(),
            'freeze_days_total' => $s->getFreezeDaysTotal(),
            'freeze_days_used' => $s->getFreezeDaysUsed(),
            'is_frozen' => $s->getStatus() === 'frozen',
            'price' => $plan->getPrice(),
            'club_id' => $s->getClub()?->getId(),
            'club_name' => $s->getClub()?->getName(),
        ];
    }
}


