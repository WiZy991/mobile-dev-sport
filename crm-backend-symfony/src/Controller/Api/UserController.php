<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Sale;
use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\CurrentUserResolver;
use App\Service\MobileClientPayloadApplier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/user')]
class UserController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly MobileClientPayloadApplier $mobileClientPayloadApplier,
        private readonly MobileAuthTokenIssuer $mobileTokens,
    ) {}

    #[Route('/stats', name: 'api_user_stats', methods: ['GET'])]
    public function stats(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $visitCount = (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.user = :user')
            ->andWhere('a.eventType = :entry')
            ->andWhere('a.result = :granted')
            ->setParameter('user', $user)
            ->setParameter('entry', 'entry')
            ->setParameter('granted', 'granted')
            ->getQuery()
            ->getSingleScalarResult();

        $streakDays = $this->computeStreak($user);

        $achievements = [];
        if ($visitCount >= 1) {
            $achievements[] = ['id' => 'first_visit', 'name' => 'Первое посещение', 'description' => 'Добро пожаловать в зал!', 'unlocked' => true];
        }
        if ($visitCount >= 5) {
            $achievements[] = ['id' => 'regular', 'name' => 'Регулярный посетитель', 'description' => '5 посещений', 'unlocked' => true];
        }
        if ($visitCount >= 20) {
            $achievements[] = ['id' => 'enthusiast', 'name' => 'Энтузиаст', 'description' => '20 посещений', 'unlocked' => true];
        }
        if ($visitCount >= 50) {
            $achievements[] = ['id' => 'veteran', 'name' => 'Ветеран', 'description' => '50 посещений', 'unlocked' => true];
        }
        if ($streakDays >= 3) {
            $achievements[] = ['id' => 'streak_3', 'name' => 'Серия 3 дня', 'description' => '3 дня подряд', 'unlocked' => true];
        }
        if ($streakDays >= 7) {
            $achievements[] = ['id' => 'streak_7', 'name' => 'Недельная серия', 'description' => '7 дней подряд', 'unlocked' => true];
        }

        return $this->json([
            'total_visits' => $visitCount,
            'streak_days' => $streakDays,
            'achievements' => $achievements,
        ]);
    }

    #[Route('/purchases', name: 'api_user_purchases', methods: ['GET'])]
    public function purchases(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        $sales = $this->em->getRepository(Sale::class)->findBy(
            ['user' => $user],
            ['createdAt' => 'DESC'],
            100
        );

        $list = array_map(static function (Sale $s) {
            return [
                'id' => (string) $s->getId(),
                'product_name' => $s->getProductName(),
                'quantity' => $s->getQuantity(),
                'price' => $s->getPrice(),
                'total' => $s->getTotal(),
                'payment_method' => $s->getPaymentMethod(),
                'created_at' => $s->getCreatedAt()->format('Y-m-d\TH:i:s'),
            ];
        }, $sales);

        return $this->json($list);
    }

    #[Route('/profile', name: 'api_user_profile_get', methods: ['GET'])]
    public function profile(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        return $this->json($this->serializeUserProfile($user));
    }

    #[Route('/profile', name: 'api_user_profile_update', methods: ['PUT'])]
    public function updateProfile(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];

        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }

        if (isset($data['email'])) {
            $user->setEmail($data['email']);
        }
        if (isset($data['name'])) {
            $user->setName($data['name']);
        }
        if (isset($data['phone'])) {
            $user->setPhone($data['phone']);
        }

        $this->mobileClientPayloadApplier->applyProfilePatch($user, $data);

        $this->em->persist($user);
        $this->em->flush();

        return $this->json($this->serializeUserProfile($user));
    }

    /** @return array<string, mixed> */
    private function serializeUserProfile(User $user): array
    {
        return array_merge($this->mobileTokens->userArray($user), [
            'gender' => $user->getGender(),
            'passport_series' => $user->getPassportSeries(),
            'passport_number' => $user->getPassportNumber(),
            'passport_issued_by' => $user->getPassportIssuedBy(),
            'passport_issue_date' => $user->getPassportIssueDate()?->format('Y-m-d'),
            'registration_address' => $user->getRegistrationAddress(),
        ]);
    }

    private function computeStreak(User $user): int
    {
        $logs = $this->em->getRepository(AccessLog::class)->findBy(
            ['user' => $user, 'eventType' => 'entry', 'result' => 'granted'],
            ['createdAt' => 'DESC'],
            60
        );

        $dates = [];
        foreach ($logs as $log) {
            $d = $log->getCreatedAt()->format('Y-m-d');
            if (!in_array($d, $dates, true)) {
                $dates[] = $d;
            }
        }
        $dates = array_values(array_unique($dates));

        if (empty($dates)) {
            return 0;
        }

        $today = (new \DateTimeImmutable('today'))->format('Y-m-d');
        $yesterday = (new \DateTimeImmutable('yesterday'))->format('Y-m-d');

        $firstDate = $dates[0];
        if ($firstDate !== $today && $firstDate !== $yesterday) {
            return 0;
        }

        $streak = 0;
        $expected = $firstDate;
        foreach ($dates as $d) {
            if ($d !== $expected) {
                break;
            }
            $streak++;
            $expected = (new \DateTimeImmutable($d . ' -1 day'))->format('Y-m-d');
        }
        return $streak;
    }
}

