<?php

declare(strict_types=1);

namespace App\Service\Staff;

use App\Entity\Club;
use App\Entity\Organization;
use App\Entity\StaffClubRental;
use App\Entity\StaffUser;
use App\Service\Admin\ClubSettingsStore;
use App\Service\ClubTimezone;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Аренда тренера по клубам (30 дней, отдельный paid_until на каждый зал).
 */
final class StaffClubRentalService
{
    public const RENTAL_DAYS = 30;

    /** Fallback, если в CRM не задана ни цена клуба, ни общая настройка. */
    private const DEFAULT_AMOUNT_RUB = 25000;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClubSettingsStore $clubSettings,
    ) {
    }

    /**
     * Каталог залов для оплаты: все клубы из CRM (как в «Клубы и шлюзы»).
     * Цена: поле клуба → настройка «Сумма аренды» → дефолт.
     *
     * @return list<Club>
     */
    public function catalogClubs(?StaffUser $staff = null): array
    {
        $this->ensureClubsHaveRentalPrices($staff?->getOrganization());

        /** @var list<Club> $clubs */
        $clubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC', 'id' => 'ASC']);

        return $clubs;
    }

    /**
     * Если у клуба нет цены — подставить сумму из настроек CRM (то же поле, что в «Настройки клуба»).
     */
    private function ensureClubsHaveRentalPrices(?Organization $organization): void
    {
        try {
            $default = $this->defaultAmountRub($organization);
            $changed = false;
            /** @var list<Club> $clubs */
            $clubs = $this->em->getRepository(Club::class)->findAll();
            foreach ($clubs as $club) {
                $own = $club->getTrainerRentalAmountRub();
                if ($own === null || $own <= 0) {
                    $club->setTrainerRentalAmountRub($default);
                    $changed = true;
                }
            }
            if ($changed) {
                $this->em->flush();
            }
        } catch (\Throwable) {
            // Нет колонки trainer_rental_amount_rub — нужен migrate; каталог всё равно отдадим.
        }
    }

    /**
     * Цена 30 дней: цена клуба → настройка CRM → дефолт.
     */
    public function amountRubForClub(Club $club, ?StaffUser $staff = null): int
    {
        $own = $club->getTrainerRentalAmountRub();
        if ($own !== null && $own > 0) {
            return $own;
        }

        return $this->defaultAmountRub($staff?->getOrganization() ?? $club->getOrganization());
    }

    public function amountKopecksForClub(Club $club, ?StaffUser $staff = null): int
    {
        return $this->amountRubForClub($club, $staff) * 100;
    }

    private function defaultAmountRub(?Organization $organization): int
    {
        if ($organization !== null) {
            $raw = trim((string) ($this->clubSettings->get('trainer_rental_amount_kopecks', $organization) ?? ''));
            if ($raw !== '' && ctype_digit($raw)) {
                return max(1, (int) round(((int) $raw) / 100));
            }
            $rub = trim((string) ($this->clubSettings->get('trainer_rental_amount_rub', $organization) ?? ''));
            if ($rub !== '' && is_numeric($rub) && (float) $rub > 0) {
                return max(1, (int) round((float) $rub));
            }
        }

        // Без tenant context — пробуем глобально через текущий TenantContext внутри store.
        $raw = trim((string) ($this->clubSettings->get('trainer_rental_amount_kopecks') ?? ''));
        if ($raw !== '' && ctype_digit($raw)) {
            return max(1, (int) round(((int) $raw) / 100));
        }
        $rub = trim((string) ($this->clubSettings->get('trainer_rental_amount_rub') ?? ''));
        if ($rub !== '' && is_numeric($rub) && (float) $rub > 0) {
            return max(1, (int) round((float) $rub));
        }

        return self::DEFAULT_AMOUNT_RUB;
    }

    public function findRental(StaffUser $staff, Club $club): ?StaffClubRental
    {
        return $this->em->getRepository(StaffClubRental::class)->findOneBy([
            'staffUser' => $staff,
            'club' => $club,
        ]);
    }

    public function hasValidRentalForClub(StaffUser $staff, Club $club, ?\DateTimeImmutable $now = null): bool
    {
        $rental = $this->findRental($staff, $club);

        return $rental !== null && $rental->isValid($now);
    }

    public function hasAnyValidRental(StaffUser $staff, ?\DateTimeImmutable $now = null): bool
    {
        foreach ($this->rentalsForStaff($staff) as $rental) {
            if ($rental->isValid($now)) {
                return true;
            }
        }

        // Legacy: до миграции на per-club.
        return $staff->hasValidRental($now);
    }

    /**
     * @return list<StaffClubRental>
     */
    public function rentalsForStaff(StaffUser $staff): array
    {
        /** @var list<StaffClubRental> $rows */
        $rows = $this->em->getRepository(StaffClubRental::class)->findBy(
            ['staffUser' => $staff],
            ['id' => 'ASC'],
        );

        return $rows;
    }

    /**
     * Продлить аренду на RENTAL_DAYS от max(now, current paid_until).
     */
    public function extendRental(StaffUser $staff, Club $club, int $days = self::RENTAL_DAYS): StaffClubRental
    {
        $now = ClubTimezone::now();
        $rental = $this->findRental($staff, $club);
        if ($rental === null || !$rental->isValid($now)) {
            $base = $now;
        } else {
            $base = $rental->getPaidUntil();
        }
        $paidUntil = new \DateTimeImmutable(
            $base->modify(sprintf('+%d days', max(1, $days)))->format('Y-m-d') . ' 23:59:59',
            ClubTimezone::zone(),
        );

        if ($rental === null) {
            $rental = new StaffClubRental($staff, $club, $paidUntil);
            $this->em->persist($rental);
        } else {
            $rental->setPaidUntil($paidUntil);
        }

        if ($staff->getActiveClub() === null) {
            $staff->setActiveClub($club);
        }

        $this->em->flush();
        $this->syncLegacyPaidUntil($staff);
        $this->em->flush();

        return $rental;
    }

    public function syncLegacyPaidUntil(StaffUser $staff): void
    {
        $max = null;
        foreach ($this->rentalsForStaff($staff) as $rental) {
            $until = $rental->getPaidUntil();
            if ($max === null || $until > $max) {
                $max = $until;
            }
        }
        $staff->setRentalPaidUntil($max);
    }

    /**
     * @return array{ok: true}|array{error: array<string, mixed>, status: int}
     */
    public function setActiveClub(StaffUser $staff, int $clubId): array
    {
        $club = $this->em->getRepository(Club::class)->find($clubId);
        if (!$club instanceof Club) {
            return [
                'error' => ['error' => 'Клуб не найден', 'code' => 'club_not_found'],
                'status' => 404,
            ];
        }
        if (!$this->hasValidRentalForClub($staff, $club)) {
            return [
                'error' => ['error' => 'Нет активной аренды для этого зала', 'code' => 'rental_inactive'],
                'status' => 400,
            ];
        }
        $staff->setActiveClub($club);
        $this->em->flush();

        return ['ok' => true];
    }

    /**
     * @return list<array<string, mixed>>
     */
    public function serializeClubs(StaffUser $staff): array
    {
        $activeId = $staff->getActiveClub()?->getId();
        $out = [];
        foreach ($this->catalogClubs($staff) as $club) {
            $rental = $this->findRental($staff, $club);
            $amountRub = $this->amountRubForClub($club, $staff);
            $paidUntil = $rental?->getPaidUntil();
            $active = $rental !== null && $rental->isValid();
            $out[] = [
                'club_id' => $club->getId(),
                'name' => $club->getName(),
                'address' => $club->getAddress(),
                'amount_rub' => $amountRub,
                'amount_kopecks' => $amountRub * 100,
                'paid_until' => $paidUntil?->format('Y-m-d\TH:i:s'),
                'rental_active' => $active,
                'is_active_club' => $activeId !== null && $activeId === $club->getId(),
                'days' => self::RENTAL_DAYS,
            ];
        }

        return $out;
    }

    public function resolveCatalogClub(StaffUser $staff, int $clubId): ?Club
    {
        foreach ($this->catalogClubs($staff) as $club) {
            if ($club->getId() === $clubId) {
                return $club;
            }
        }

        return null;
    }
}
