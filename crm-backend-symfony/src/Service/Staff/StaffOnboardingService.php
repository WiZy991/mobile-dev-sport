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
    public const GATE_ACTIVE = 'active';

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
            default => $user->requiresTrainerRental() && !$user->hasValidRental()
                ? self::GATE_NEEDS_PAYMENT
                : self::GATE_ACTIVE,
        };
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

        return [
            'status' => $gate,
            'registration_status' => $user->getRegistrationStatus(),
            'requires_rental' => $user->requiresTrainerRental(),
            'rental_paid_until' => $user->getRentalPaidUntil()?->format('Y-m-d\TH:i:s'),
            'offer_accepted_at' => $user->getOfferAcceptedAt()?->format('Y-m-d\TH:i:s'),
            'offer_url' => $offerUrl,
            'rental_amount_kopecks' => $amount,
            'rental_amount_rub' => round($amount / 100, 2),
            'trainer_id' => $user->getTrainer()?->getId() !== null
                ? 'trainer-' . $user->getTrainer()->getId()
                : null,
        ];
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
            ->setSpecialization('Персональный тренер')
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
