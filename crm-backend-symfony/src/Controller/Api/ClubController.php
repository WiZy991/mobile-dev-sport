<?php

namespace App\Controller\Api;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\Product;
use App\Entity\Promotion;
use App\Entity\SubscriptionPlan;
use App\Service\Admin\ClubSettingsStore;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/club')]
class ClubController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClubSettingsStore $clubSettings,
    ) {}

    #[Route('/occupancy', name: 'api_club_occupancy', methods: ['GET'])]
    public function occupancy(): JsonResponse
    {
        $maxCapacity = 100;
        $capRaw = $this->clubSettings->get('gym_max_capacity');
        if ($capRaw !== null && $capRaw !== '') {
            $maxCapacity = max(10, (int) $capRaw);
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

    private function legalUrlsFromSettings(): array
    {
        $offer = $this->normalizeClubOfferUrl(trim((string) ($this->clubSettings->get('offer_url') ?? '')));
        $privacy = $this->normalizeClubPrivacyUrl(trim((string) ($this->clubSettings->get('privacy_url') ?? '')));
        $visiting = trim((string) ($this->clubSettings->get('visiting_rules_url') ?? ''));
        $safety = trim((string) ($this->clubSettings->get('safety_rules_url') ?? ''));

        return [
            'offer_url' => $offer !== '' ? $offer : 'https://dobrozal.ru/doc/offer',
            'privacy_url' => $privacy !== '' ? $privacy : 'https://dobrozal.ru/doc/privacy',
            'visiting_rules_url' => $visiting !== '' ? $visiting : null,
            'safety_rules_url' => $safety !== '' ? $safety : null,
        ];
    }

    /**
     * В CRM иногда ошибочно указывают документы WorldCashFit вместо оферты клуба (ИП).
     */
    private function normalizeClubOfferUrl(string $url): string
    {
        if ($url === '') {
            return '';
        }
        $lower = strtolower($url);
        if (
            str_contains($lower, 'license_agreement')
            || str_contains($lower, 'user_agreement')
            || str_contains($lower, 'worldcashfit.ru/client-agreement')
            || str_contains($lower, 'worldcashfit.ru/trainer-agreement')
        ) {
            return 'https://dobrozal.ru/doc/offer';
        }

        return $url;
    }

    private function normalizeClubPrivacyUrl(string $url): string
    {
        if ($url === '') {
            return '';
        }
        $lower = strtolower($url);
        if (str_contains($lower, 'worldcashfit.ru/privacy')) {
            return 'https://dobrozal.ru/doc/privacy';
        }

        return $url;
    }

    public function list(): JsonResponse
    {
        $clubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);
        if (empty($clubs)) {
            // Пустая таблица clubs (частый случай после миграций без сида): отдаём один «виртуальный»
            // клуб из настроек CRM — тот же источник, что и GET /club/info.
            return $this->json([$this->defaultClubListItemFromSettings()]);
        }
        $legal = $this->legalUrlsFromSettings();
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
            ...$legal,
        ], $clubs);
        return $this->json($list);
    }

    /**
     * Один клуб для списка регистрации, если в БД ещё нет строк в `clubs`.
     * id = "default" — не PK в таблице; регистрация в API пока не привязывает пользователя к клубу.
     *
     * @return array<string, mixed>
     */
    private function defaultClubListItemFromSettings(): array
    {
        $get = function (string $key, string $default): string {
            return $this->clubSettings->get($key) ?? $default;
        };

        $amenitiesStr = $get('amenities', 'Тренажёрный зал, Бассейн, Йога, Групповые занятия');
        $amenities = array_values(array_map('trim', array_filter(explode(',', $amenitiesStr))));

        $maxCapacity = null;
        $capRaw = $this->clubSettings->get('gym_max_capacity');
        if ($capRaw !== null && $capRaw !== '') {
            $maxCapacity = max(10, (int) $capRaw);
        }

        $clubName = $get('name', 'Доброзал');
        // Если в настройках остался "плейсхолдер" по умолчанию, подставляем корректное название из требований.
        if (trim((string) $clubName) === 'FitnessClub') {
            $clubName = 'Доброзал';
        }

        return [
            'id' => 'default',
            'name' => $clubName,
            'address' => $get('address', 'г. Москва, ул. Примерная, д. 1'),
            'phone' => $get('phone', '+7 (495) 123-45-67'),
            'email' => $get('email', 'info@fitnessclub.ru'),
            'working_hours' => $get('working_hours', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00'),
            'latitude' => (float) $get('latitude', '55.7558'),
            'longitude' => (float) $get('longitude', '37.6173'),
            'amenities' => $amenities,
            'max_capacity' => $maxCapacity,
            ...$this->legalUrlsFromSettings(),
        ];
    }

    public function show(int $id): JsonResponse
    {
        $club = $this->em->getRepository(Club::class)->find($id);
        if (!$club) {
            return $this->json(['error' => 'Club not found'], 404);
        }
        $legal = $this->legalUrlsFromSettings();

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
            ...$legal,
        ]);
    }

    #[Route('/info', name: 'api_club_info', methods: ['GET'])]
    public function info(): JsonResponse
    {
        $get = function (string $key, string $default): string {
            return $this->clubSettings->get($key) ?? $default;
        };

        $amenitiesStr = $get('amenities', 'Тренажёрный зал, Бассейн, Йога, Групповые занятия');
        $amenities = array_map('trim', array_filter(explode(',', $amenitiesStr)));

        $clubName = $get('name', 'Доброзал');
        if (trim((string) $clubName) === 'FitnessClub') {
            $clubName = 'Доброзал';
        }

        $contactPhone = trim($get('contact_phone', ''));
        $contactEmail = trim($get('contact_email', ''));

        return $this->json([
            'name' => $clubName,
            'address' => $get('address', 'г. Москва, ул. Примерная, д. 1'),
            'phone' => $contactPhone !== '' ? $contactPhone : $get('phone', '+7 (495) 123-45-67'),
            'email' => $contactEmail !== '' ? $contactEmail : $get('email', 'info@fitnessclub.ru'),
            'working_hours' => $get('working_hours', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00'),
            'amenities' => $amenities,
            'latitude' => (float) $get('latitude', '55.7558'),
            'longitude' => (float) $get('longitude', '37.6173'),
            'promo_title' => $get('promo_home_title', 'СКИДКА 20%!'),
            'promo_subtitle' => $get('promo_home_subtitle', 'на все карты 12 и 6 месяцев'),
            'shop_config' => $this->shopConfigForApi(),
            'network' => [
                'about' => $get('network_about', '') ?: null,
                'social_vk' => $get('social_vk', '') ?: null,
                'social_telegram' => $get('social_telegram', '') ?: null,
                'website' => $get('contact_website', '') ?: null,
            ],
            ...$this->legalUrlsFromSettings(),
        ]);
    }

    /**
     * @return array<string, mixed>
     */
    private function shopConfigForApi(): array
    {
        $tabOrderRaw = trim((string) ($this->clubSettings->get('shop_tab_order') ?? ''));
        $tabOrder = ['subscriptions', 'services', 'goods'];
        if ($tabOrderRaw !== '') {
            $decoded = json_decode($tabOrderRaw, true);
            if (\is_array($decoded)) {
                $tabOrder = array_values(array_filter(
                    array_map('strval', $decoded),
                    static fn (string $k) => \in_array($k, ['subscriptions', 'services', 'goods'], true),
                ));
                if ($tabOrder === []) {
                    $tabOrder = ['subscriptions', 'services', 'goods'];
                }
            }
        }

        $defaultTab = trim((string) ($this->clubSettings->get('shop_default_tab') ?? 'subscriptions'));
        if (!\in_array($defaultTab, ['subscriptions', 'services', 'goods'], true)) {
            $defaultTab = 'subscriptions';
        }

        $hideEmpty = ($this->clubSettings->get('hide_empty_shop_tabs') ?? '1') !== '0';

        $servicesCount = (int) $this->em->createQueryBuilder()
            ->select('COUNT(p.id)')
            ->from(Product::class, 'p')
            ->where('p.isActive = :active')
            ->andWhere('p.category = :cat')
            ->setParameter('active', true)
            ->setParameter('cat', 'service')
            ->getQuery()
            ->getSingleScalarResult();

        $goodsCount = (int) $this->em->createQueryBuilder()
            ->select('COUNT(p.id)')
            ->from(Product::class, 'p')
            ->where('p.isActive = :active')
            ->andWhere('p.category = :cat')
            ->setParameter('active', true)
            ->setParameter('cat', 'goods')
            ->getQuery()
            ->getSingleScalarResult();

        $subscriptionsCount = \count($this->em->getRepository(SubscriptionPlan::class)->findAll());

        return [
            'tab_order' => $tabOrder,
            'default_tab' => $defaultTab,
            'hide_empty_tabs' => $hideEmpty,
            'counts' => [
                'services' => $servicesCount,
                'goods' => $goodsCount,
                'subscriptions' => $subscriptionsCount,
            ],
        ];
    }

    #[Route('/promotions', name: 'api_club_promotions', methods: ['GET'])]
    public function promotions(Request $request): JsonResponse
    {
        $items = $this->em->getRepository(Promotion::class)->findBy(
            ['isActive' => true],
            ['sortOrder' => 'ASC', 'id' => 'DESC']
        );

        $data = array_map(fn (Promotion $p) => [
            'id' => (string) $p->getId(),
            'title' => $p->getTitle(),
            'subtitle' => $p->getSubtitle(),
            'image_url' => self::absolutePublicUrl($request, $p->getImagePath()),
            'button_text' => $p->getButtonText(),
            'action_type' => $p->getActionType(),
            'action_value' => $p->getActionValue(),
            'bg_from' => $p->getBgFrom(),
            'bg_to' => $p->getBgTo(),
            'sort_order' => $p->getSortOrder(),
        ], $items);

        return $this->json($data);
    }

    /** Полный URL к файлу в public/ (Coil и браузеры не подставляют хост к пути «/uploads/...»). */
    private static function absolutePublicUrl(Request $request, ?string $path): ?string
    {
        if ($path === null || trim($path) === '') {
            return null;
        }
        $path = trim($path);
        if (str_starts_with($path, 'http://') || str_starts_with($path, 'https://')) {
            return $path;
        }
        $base = $request->getSchemeAndHttpHost();

        return str_starts_with($path, '/') ? $base . $path : $base . '/' . $path;
    }
}
