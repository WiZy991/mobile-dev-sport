<?php

namespace App\Controller\Api;

use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Entity\User;
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
    ) {}

    #[Route('', name: 'api_subscriptions_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $user = $this->em->getRepository(User::class)->findOneBy([]); // временно текущий пользователь
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
            ];
        }, $subs);

        return $this->json($data);
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
        ];
    }
}


