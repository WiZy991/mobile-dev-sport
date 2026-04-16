<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Club;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Поля регистрации/профиля из мобильного приложения → сущность User (клиент в CRM).
 */
final class MobileClientPayloadApplier
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** При регистрации из приложения: паспорт, дата рождения, пол. */
    public function applyRegistrationPayload(User $user, array $data): void
    {
        if (!empty($data['date_of_birth'])) {
            try {
                $user->setDateOfBirth(new \DateTimeImmutable(trim((string) $data['date_of_birth'])));
            } catch (\Throwable) {
            }
        }

        $gender = $this->normalizeGender($data['gender'] ?? null);
        if ($gender !== null) {
            $user->setGender($gender);
        }

        $this->hydratePassportFromRegistration($user, $data);

        $clubRaw = $data['club_id'] ?? null;
        if ($clubRaw !== null && $clubRaw !== '') {
            $cid = (int) $clubRaw;
            if ($cid > 0) {
                $club = $this->em->getRepository(Club::class)->find($cid);
                if ($club instanceof Club) {
                    $user->setClub($club);
                }
            }
        }
    }

    /**
     * Частичное обновление профиля (PUT /api/v1/user/profile): только переданные ключи.
     */
    public function applyProfilePatch(User $user, array $data): void
    {
        if (\array_key_exists('date_of_birth', $data)) {
            $dob = $data['date_of_birth'];
            if ($dob === null || $dob === '') {
                $user->setDateOfBirth(null);
            } else {
                try {
                    $user->setDateOfBirth(new \DateTimeImmutable(trim((string) $dob)));
                } catch (\Throwable) {
                }
            }
        }

        if (\array_key_exists('gender', $data)) {
            $g = $data['gender'];
            if ($g === null || $g === '') {
                $user->setGender(null);
            } else {
                $user->setGender($this->normalizeGender((string) $g));
            }
        }

        if (\array_key_exists('passport_series', $data)) {
            $v = trim((string) ($data['passport_series'] ?? ''));
            $user->setPassportSeries($v === '' ? null : substr($v, 0, 10));
        }
        if (\array_key_exists('passport_number', $data)) {
            $v = trim((string) ($data['passport_number'] ?? ''));
            $user->setPassportNumber($v === '' ? null : substr($v, 0, 10));
        }
        if (\array_key_exists('passport_issued_by', $data)) {
            $v = trim((string) ($data['passport_issued_by'] ?? ''));
            $user->setPassportIssuedBy($v === '' ? null : substr($v, 0, 255));
        }
        if (\array_key_exists('passport_issue_date', $data)) {
            $pid = $data['passport_issue_date'];
            if ($pid === null || $pid === '') {
                $user->setPassportIssueDate(null);
            } else {
                try {
                    $user->setPassportIssueDate(new \DateTimeImmutable(trim((string) $pid)));
                } catch (\Throwable) {
                }
            }
        }
        if (\array_key_exists('registration_address', $data)) {
            $v = trim((string) ($data['registration_address'] ?? ''));
            $user->setRegistrationAddress($v === '' ? null : substr($v, 0, 255));
        }
    }

    private function hydratePassportFromRegistration(User $user, array $data): void
    {
        $series = trim((string) ($data['passport_series'] ?? ''));
        if ($series !== '') {
            $user->setPassportSeries(substr($series, 0, 10));
        }
        $number = trim((string) ($data['passport_number'] ?? ''));
        if ($number !== '') {
            $user->setPassportNumber(substr($number, 0, 10));
        }
        $issuedBy = trim((string) ($data['passport_issued_by'] ?? ''));
        if ($issuedBy !== '') {
            $user->setPassportIssuedBy(substr($issuedBy, 0, 255));
        }
        if (!empty($data['passport_issue_date'])) {
            try {
                $user->setPassportIssueDate(new \DateTimeImmutable(trim((string) $data['passport_issue_date'])));
            } catch (\Throwable) {
            }
        }
        $addr = trim((string) ($data['registration_address'] ?? ''));
        if ($addr !== '') {
            $user->setRegistrationAddress(substr($addr, 0, 255));
        }
    }

    private function normalizeGender(mixed $raw): ?string
    {
        if ($raw === null) {
            return null;
        }
        $s = strtolower(trim((string) $raw));
        if ($s === '') {
            return null;
        }

        return match ($s) {
            'male', 'm' => 'M',
            'female', 'f' => 'F',
            default => match (strtoupper(substr($s, 0, 1))) {
                'M' => 'M',
                'F' => 'F',
                default => substr((string) $raw, 0, 20),
            },
        };
    }
}
