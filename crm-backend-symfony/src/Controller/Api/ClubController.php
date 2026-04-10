<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\ClubSetting;
use App\Entity\Promotion;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/club')]
class ClubController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    #[Route('/occupancy', name: 'api_club_occupancy', methods: ['GET'])]
    public function occupancy(): JsonResponse
    {
        $maxCapacity = 100;
        $setting = $this->em->getRepository(ClubSetting::class)->find('gym_max_capacity');
        if ($setting && $setting->getSettingValue() !== null && $setting->getSettingValue() !== '') {
            $maxCapacity = max(10, (int) $setting->getSettingValue());
        }

        $today = new \DateTimeImmutable('today');
        $tomorrow = $today->modify('+1 day');

        $entries = (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.eventType = :entry')
            ->andWhere('a.result = :granted')
            ->andWhere('a.createdAt >= :start')
            ->andWhere('a.createdAt < :end')
            ->setParameter('entry', 'entry')
            ->setParameter('granted', 'granted')
            ->setParameter('start', $today)
            ->setParameter('end', $tomorrow)
            ->getQuery()
            ->getSingleScalarResult();

        $exits = (int) $this->em->createQueryBuilder()
            ->select('COUNT(a.id)')
            ->from(AccessLog::class, 'a')
            ->where('a.eventType = :exit')
            ->andWhere('a.createdAt >= :start')
            ->andWhere('a.createdAt < :end')
            ->setParameter('exit', 'exit')
            ->setParameter('start', $today)
            ->setParameter('end', $tomorrow)
            ->getQuery()
            ->getSingleScalarResult();

        $current = max(0, $entries - $exits);
        $percentage = $maxCapacity > 0 ? min(100, (int) round($current * 100 / $maxCapacity)) : 0;

        return $this->json([
            'current' => $current,
            'max_capacity' => $maxCapacity,
            'percentage' => $percentage,
            'status' => $percentage < 50 ? 'low' : ($percentage < 80 ? 'medium' : 'high'),
        ]);
    }

    public function list(): JsonResponse
    {
        $clubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
        if (empty($clubs)) {
            return $this->json([]);
        }
        $list = array_map(fn (Club $c) => [
            'id' => (string) $c->getId(),
            'name' => $c->getName(),
            'address' => $c->getAddress(),
            'phone' => $c->getPhone(),
            'email' => $c->getEmail(),
            'working_hours' => $c->getWorkingHours(),
            'latitude' => $c->getLatitude(),
            'longitude' => $c->getLongitude(),
            'amenities' => $c->getAmenities(),
            'max_capacity' => $c->getMaxCapacity(),
        ], $clubs);
        return $this->json($list);
    }

    public function show(int $id): JsonResponse
    {
        $club = $this->em->getRepository(Club::class)->find($id);
        if (!$club) {
            return $this->json(['error' => 'Club not found'], 404);
        }
        return $this->json([
            'id' => (string) $club->getId(),
            'name' => $club->getName(),
            'address' => $club->getAddress(),
            'phone' => $club->getPhone(),
            'email' => $club->getEmail(),
            'working_hours' => $club->getWorkingHours(),
            'amenities' => $club->getAmenities(),
            'latitude' => $club->getLatitude(),
            'longitude' => $club->getLongitude(),
        ]);
    }

    #[Route('/info', name: 'api_club_info', methods: ['GET'])]
    public function info(): JsonResponse
    {
        $get = function (string $key, string $default): string {
            $s = $this->em->getRepository(ClubSetting::class)->find($key);
            return $s?->getSettingValue() ?? $default;
        };

        $amenitiesStr = $get('amenities', 'Тренажёрный зал, Бассейн, Йога, Групповые занятия');
        $amenities = array_map('trim', array_filter(explode(',', $amenitiesStr)));

        return $this->json([
            'name' => $get('name', 'FitnessClub'),
            'address' => $get('address', 'г. Москва, ул. Примерная, д. 1'),
            'phone' => $get('phone', '+7 (495) 123-45-67'),
            'email' => $get('email', 'info@fitnessclub.ru'),
            'working_hours' => $get('working_hours', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00'),
            'amenities' => $amenities,
            'latitude' => (float) $get('latitude', '55.7558'),
            'longitude' => (float) $get('longitude', '37.6173'),
            'promo_title' => $get('promo_home_title', 'СКИДКА 20%!'),
            'promo_subtitle' => $get('promo_home_subtitle', 'на все карты 12 и 6 месяцев'),
        ]);
    }

    #[Route('/promotions', name: 'api_club_promotions', methods: ['GET'])]
    public function promotions(): JsonResponse
    {
        $items = $this->em->getRepository(Promotion::class)->findBy(
            ['isActive' => true],
            ['sortOrder' => 'ASC', 'id' => 'DESC']
        );

        $data = array_map(static fn (Promotion $p) => [
            'id' => (string) $p->getId(),
            'title' => $p->getTitle(),
            'subtitle' => $p->getSubtitle(),
            'button_text' => $p->getButtonText(),
            'action_type' => $p->getActionType(),
            'action_value' => $p->getActionValue(),
            'bg_from' => $p->getBgFrom(),
            'bg_to' => $p->getBgTo(),
            'sort_order' => $p->getSortOrder(),
        ], $items);

        return $this->json($data);
    }
}
