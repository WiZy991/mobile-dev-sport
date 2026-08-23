<?php

declare(strict_types=1);

namespace App\Service\Staff;

use App\Entity\Organization;
use App\Entity\StaffUser;
use App\Entity\Trainer;
use App\Service\Admin\ClubSettingsStore;
use Doctrine\ORM\EntityManagerInterface;

final class StaffOnboardingService
{
    public const GATE_PENDING = 'pending_approval';
    public const GATE_REJECTED = 'rejected';
    public const GATE_NEEDS_PAYMENT = 'needs_offer_payment';
    /** Одобрен и оплатил аренду, но не заполнил обязательные поля карточки (п.16 репорта). */
    public const GATE_NEEDS_PROFILE = 'needs_profile';
    public const GATE_ACTIVE = 'active';

    /** Максимум специализаций в карточке тренера. */
    public const MAX_SPECIALIZATIONS = 5;

    /**
     * Справочник специализаций для мультивыбора (п.17 репорта).
     *
     * @return list<string>
     */
    public static function specializationCatalog(): array
    {
        return [
            'Персональный тренер',
            'Силовые тренировки',
            'Функциональный тренинг',
            'Кроссфит',
            'Йога',
            'Пилатес',
            'Стретчинг',
            'Кардио',
            'Бокс / единоборства',
            'Реабилитация',
            'Похудение',
            'Набор массы',
            'Подготовка к соревнованиям',
            'Детский фитнес',
            'Групповые программы',
        ];
    }

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClubSettingsStore $clubSettings,
    ) {
    }

    public function resolveGate(StaffUser $user): string
    {
        return match ($user->getRegistrationStatus()) {
            StaffUser::REGISTRATION_PENDING => self::GATE_PENDING,
            StaffUser::REGISTRATION_REJECTED => self::GATE_REJECTED,
            default => $this->resolveApprovedGate($user),
        };
    }

    private function resolveApprovedGate(StaffUser $user): string
    {
        if ($user->requiresTrainerRental() && !$user->hasValidRental()) {
            return self::GATE_NEEDS_PAYMENT;
        }
        if ($user->isTrainerRole() && !$this->isTrainerProfileComplete($user)) {
            return self::GATE_NEEDS_PROFILE;
        }

        return self::GATE_ACTIVE;
    }

    /**
     * Обязательные поля карточки: телефон + хотя бы одна специализация из справочника.
     */
    public function isTrainerProfileComplete(StaffUser $user): bool
    {
        $trainer = $user->getTrainer();
        if ($trainer === null) {
            return false;
        }
        $phone = preg_replace('/\D+/', '', (string) ($trainer->getPhone() ?? '')) ?? '';
        // Российский мобильный: 10 цифр или 11 с ведущей 7/8.
        $phoneOk = (\strlen($phone) === 10)
            || (\strlen($phone) === 11 && ($phone[0] === '7' || $phone[0] === '8'));
        $specs = $this->parseSpecializations((string) ($trainer->getSpecialization() ?? ''));

        return $phoneOk && $specs !== [];
    }

    /**
     * @return list<string>
     */
    public function parseSpecializations(string $raw): array
    {
        $catalog = array_map('mb_strtolower', self::specializationCatalog());
        $parts = preg_split('/[,;|]+/u', $raw) ?: [];
        $out = [];
        foreach ($parts as $part) {
            $label = trim($part);
            if ($label === '') {
                continue;
            }
            $idx = array_search(mb_strtolower($label), $catalog, true);
            if ($idx === false) {
                continue;
            }
            $canonical = self::specializationCatalog()[$idx];
            if (!\in_array($canonical, $out, true)) {
                $out[] = $canonical;
            }
            if (\count($out) >= self::MAX_SPECIALIZATIONS) {
                break;
            }
        }

        return $out;
    }

    public function normalizeSpecializationInput(mixed $raw): string
    {
        if (\is_array($raw)) {
            $joined = implode(', ', array_map('strval', $raw));
        } else {
            $joined = (string) $raw;
        }
        $parsed = $this->parseSpecializations($joined);

        return implode(', ', $parsed);
    }

    /** @return array<string, mixed> */
    public function serialize(StaffUser $user): array
    {
        $gate = $this->resolveGate($user);
        $amount = $this->rentalAmountKopecks();
        $offerUrl = trim((string) ($this->clubSettings->get('offer_url') ?? ''));
        if ($offerUrl === '') {
            $offerUrl = 'https://dobrozal.ru/doc/offer';
        }
        $privacyUrl = trim((string) ($this->clubSettings->get('privacy_url') ?? ''));
        if ($privacyUrl === '') {
            $privacyUrl = 'https://dobrozal.ru/doc/privacy';
        }
        $docsUrl = trim((string) ($this->clubSettings->get('legal_docs_url') ?? ''));
        if ($docsUrl === '') {
            $docsUrl = 'https://dobrozal.ru/doc';
        }

        $trainer = $user->getTrainer();
        $missing = [];
        if ($user->isTrainerRole()) {
            if ($trainer === null || trim((string) ($trainer->getPhone() ?? '')) === '') {
                $missing[] = 'phone';
            }
            if ($trainer === null || $this->parseSpecializations((string) ($trainer->getSpecialization() ?? '')) === []) {
                $missing[] = 'specialization';
            }
        }

        return [
            'status' => $gate,
            'registration_status' => $user->getRegistrationStatus(),
            'staff_user_id' => $user->getId(),
            'requires_rental' => $user->requiresTrainerRental(),
            'rental_paid_until' => $user->getRentalPaidUntil()?->format('Y-m-d\TH:i:s'),
            'rental_active' => !$user->requiresTrainerRental() || $user->hasValidRental(),
            'offer_accepted_at' => $user->getOfferAcceptedAt()?->format('Y-m-d\TH:i:s'),
            'offer_url' => $offerUrl,
            'privacy_url' => $privacyUrl,
            'docs_url' => $docsUrl,
            'rental_amount_kopecks' => $amount,
            'rental_amount_rub' => round($amount / 100, 2),
            'rental_plans' => $this->rentalPlans($amount),
            'trainer_id' => $trainer?->getId() !== null
                ? 'trainer-' . $trainer->getId()
                : null,
            'profile_complete' => $this->isTrainerProfileComplete($user),
            'profile_missing' => $missing,
            'specializations_catalog' => self::specializationCatalog(),
            'specializations_max' => self::MAX_SPECIALIZATIONS,
        ];
    }

    /**
     * Тарифы аренды: база × N месяцев (1 / 3 / 6).
     *
     * @return list<array{months: int, label: string, amount_kopecks: int, amount_rub: float}>
     */
    public function rentalPlans(int $baseKopecks): array
    {
        $base = max(0, $baseKopecks);
        $plans = [];
        foreach ([1, 3, 6] as $months) {
            $amount = $base * $months;
            $plans[] = [
                'months' => $months,
                'label' => match ($months) {
                    1 => '1 месяц',
                    3 => '3 месяца',
                    6 => '6 месяцев',
                    default => $months . ' мес.',
                },
                'amount_kopecks' => $amount,
                'amount_rub' => round($amount / 100, 2),
            ];
        }

        return $plans;
    }

    public function normalizeRentalMonths(int $months): int
    {
        return \in_array($months, [1, 3, 6], true) ? $months : 1;
    }

    public function rentalAmountKopecks(): int
    {
        $raw = trim((string) ($this->clubSettings->get('trainer_rental_amount_kopecks') ?? ''));
        if ($raw !== '' && ctype_digit($raw)) {
            return max(0, (int) $raw);
        }
        // Fallback: rubles in setting
        $rub = trim((string) ($this->clubSettings->get('trainer_rental_amount_rub') ?? '5000'));
        $rubFloat = is_numeric($rub) ? (float) $rub : 5000.0;

        return (int) round(max(0, $rubFloat) * 100);
    }

    public function ensureTrainerProfile(StaffUser $user): Trainer
    {
        $existing = $user->getTrainer();
        if ($existing instanceof Trainer) {
            return $existing;
        }

        $org = $user->getOrganization()
            ?? $this->em->getRepository(Organization::class)->findOneBy([]);
        if ($org === null) {
            throw new \RuntimeException('Нельзя создать карточку тренера без организации');
        }

        $trainer = (new Trainer())
            ->setName($user->getName() !== '' ? $user->getName() : $user->getEmail())
            ->setSpecialization('') // пусто — пока тренер не выберет из справочника (п.16/17)
            ->setPublicationStatus(Trainer::STATUS_MODERATION)
            ->setOrganization($org);
        $this->em->persist($trainer);
        $user->setTrainer($trainer);
        $this->em->flush();

        return $trainer;
    }

    public function approve(StaffUser $user): void
    {
        $user
            ->setRegistrationStatus(StaffUser::REGISTRATION_APPROVED)
            ->setIsActive(true);
        $this->ensureTrainerProfile($user);
        $this->em->flush();
    }

    public function reject(StaffUser $user): void
    {
        $user
            ->setRegistrationStatus(StaffUser::REGISTRATION_REJECTED)
            ->setIsActive(false);
        $this->em->flush();
    }

    public function canAccessWorkApis(StaffUser $user): bool
    {
        return $this->resolveGate($user) === self::GATE_ACTIVE;
    }
}
