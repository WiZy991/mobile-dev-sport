<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Заполнение профиля User из данных userinfo/id_token по Сбер ID (см. WorldFitness/instruction.txt).
 */
final class SberIdProfileApplicator
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
    }

    /** @param array<string, mixed> $merged Плоский объект userinfo после array_merge(id_token_claims, userinfo). */
    public function apply(User $user, array $merged): void
    {
        $this->applyEmail($user, $merged);
        $this->applyPhone($user, $merged);
        $this->applyName($user, $merged);
        $this->applyBirthGenderPlaceAddress($user, $merged);
        $this->applyPassportFromSberDocs($user, $merged);
    }

    /** Для поиска существующего клиента до полного merge (контроллер Сбер ID). */
    public function extractEmailForLookup(array $merged): ?string
    {
        return $this->pickEmail($merged);
    }

    public function extractPhoneForLookup(array $merged): ?string
    {
        return $this->pickPhone($merged);
    }

    public function extractDisplayName(array $merged): string
    {
        return $this->pickDisplayName($merged);
    }

    /** Есть ли в ответе Сбера блок паспорта (scope maindoc / priority_doc). */
    public function hasPassportPayload(array $merged): bool
    {
        return $this->extractPassportDocument($merged) !== null;
    }

    /** @param array<string, mixed> $merged */
    private function applyEmail(User $user, array $merged): void
    {
        $email = $this->pickEmail($merged);
        if ($email === null || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return;
        }

        $other = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
        if ($other !== null && $other->getId() !== $user->getId()) {
            return;
        }

        $user->setEmail($email);
    }

    /** @param array<string, mixed> $merged */
    private function applyPhone(User $user, array $merged): void
    {
        $phone = $this->pickPhone($merged);
        if ($phone !== null && $phone !== '') {
            $user->setPhone($phone);
        }
    }

    /** @param array<string, mixed> $merged */
    private function applyName(User $user, array $merged): void
    {
        $name = $this->pickDisplayName($merged);
        if ($name !== '' && $name !== 'Клиент Сбер ID') {
            $user->setName($name);
        }
    }

    /** @param array<string, mixed> $merged */
    private function applyBirthGenderPlaceAddress(User $user, array $merged): void
    {
        if (!empty($merged['birthdate']) && is_string($merged['birthdate'])) {
            $d = self::parseSberFlexibleDate(trim($merged['birthdate']));
            if ($d !== null) {
                $user->setDateOfBirth($d);
            }
        }

        if (isset($merged['gender'])) {
            $gLabel = self::normalizeSberGender($merged['gender']);
            if ($gLabel !== null) {
                $user->setGender($gLabel);
            }
        }

        if (!empty($merged['place_of_birth']) && is_string($merged['place_of_birth'])) {
            $p = trim($merged['place_of_birth']);
            if ($p !== '') {
                $user->setPlaceOfBirth(mb_substr($p, 0, 255));
            }
        }

        foreach (['address_reg', 'address'] as $addrKey) {
            if (!empty($merged[$addrKey]) && is_array($merged[$addrKey])) {
                /** @var array<string, mixed> $addr */
                $addr = $merged[$addrKey];
                $full = isset($addr['full_address']) && is_string($addr['full_address'])
                    ? trim($addr['full_address']) : '';
                if ($full !== '') {
                    $user->setRegistrationAddress(mb_substr($full, 0, 255));

                    break;
                }
            }
        }
    }

    /** @param array<string, mixed> $merged */
    private function applyPassportFromSberDocs(User $user, array $merged): void
    {
        $doc = $this->extractPassportDocument($merged);
        if ($doc === null) {
            return;
        }

        if (isset($doc['series']) && is_scalar($doc['series'])) {
            $s = preg_replace('/\s+/', ' ', trim((string) $doc['series'])) ?: '';
            if ($s !== '') {
                $user->setPassportSeries(mb_substr($s, 0, 10));
            }
        }
        if (isset($doc['number']) && is_scalar($doc['number'])) {
            $n = preg_replace('/\s+/', '', (string) $doc['number']) ?: '';
            if ($n !== '') {
                $user->setPassportNumber(mb_substr($n, 0, 10));
            }
        }
        if (isset($doc['issued_by']) && is_scalar($doc['issued_by'])) {
            $by = trim((string) $doc['issued_by']);
            if ($by !== '') {
                $user->setPassportIssuedBy(mb_substr($by, 0, 255));
            }
        }
        if (isset($doc['issued_date']) && is_scalar($doc['issued_date'])) {
            $id = self::parseSberFlexibleDate(trim((string) $doc['issued_date']));
            if ($id !== null) {
                $user->setPassportIssueDate($id);
            }
        }
        if (isset($doc['code']) && is_scalar($doc['code'])) {
            $c = preg_replace('/\s+/', '', trim((string) $doc['code'])) ?: '';
            if ($c !== '') {
                $user->setPassportDepartmentCode(mb_substr($c, 0, 10));
            }
        }
    }

    /**
     * Паспорт в userinfo: scope maindoc → identification, priority_doc (instruction.txt).
     *
     * @param array<string, mixed> $merged
     *
     * @return array<string, mixed>|null
     */
    private function extractPassportDocument(array $merged): ?array
    {
        foreach (['identification', 'priority_doc', 'maindoc', 'previous_identification'] as $key) {
            if (!array_key_exists($key, $merged)) {
                continue;
            }
            $doc = self::normalizeDocNode($merged[$key]);
            if ($doc !== null && self::docLooksLikePassport($doc)) {
                return $doc;
            }
        }

        $series = isset($merged['passport_series']) && is_scalar($merged['passport_series'])
            ? trim((string) $merged['passport_series']) : '';
        $number = isset($merged['passport_number']) && is_scalar($merged['passport_number'])
            ? trim((string) $merged['passport_number']) : '';
        if ($series !== '' || $number !== '') {
            return [
                'series' => $series,
                'number' => $number,
                'issued_by' => $merged['passport_issued_by'] ?? null,
                'issued_date' => $merged['passport_issue_date'] ?? null,
                'code' => $merged['passport_department_code'] ?? null,
            ];
        }

        return null;
    }

    /** @return array<string, mixed>|null */
    private static function normalizeDocNode(mixed $node): ?array
    {
        if (is_string($node)) {
            $node = trim($node);
            if ($node === '') {
                return null;
            }
            try {
                $decoded = json_decode($node, true, 512, JSON_THROW_ON_ERROR);
            } catch (\JsonException) {
                return null;
            }

            return is_array($decoded) ? $decoded : null;
        }

        return is_array($node) ? $node : null;
    }

    /** @param array<string, mixed> $doc */
    private static function docLooksLikePassport(array $doc): bool
    {
        foreach (['number', 'series'] as $k) {
            if (!isset($doc[$k]) || !is_scalar($doc[$k])) {
                continue;
            }
            if (trim((string) $doc[$k]) !== '') {
                return true;
            }
        }

        return false;
    }

    /** @param array<string, mixed> $merged */
    private function pickEmail(array $merged): ?string
    {
        foreach (['email', 'preferred_username'] as $k) {
            if (isset($merged[$k]) && is_string($merged[$k])) {
                $e = trim($merged[$k]);
                if ($e !== '') {
                    return $e;
                }
            }
        }

        return null;
    }

    /** @param array<string, mixed> $merged */
    private function pickPhone(array $merged): ?string
    {
        foreach (['phone_number', 'mobile', 'tel'] as $k) {
            if (isset($merged[$k]) && is_string($merged[$k])) {
                $p = trim($merged[$k]);
                if ($p !== '') {
                    return $this->formatRussianPhoneDigits($p);
                }
            }
        }

        return null;
    }

    /** @param array<string, mixed> $merged */
    private function pickDisplayName(array $merged): string
    {
        $family = isset($merged['family_name']) && is_string($merged['family_name']) ? trim($merged['family_name']) : '';
        $given = isset($merged['given_name']) && is_string($merged['given_name']) ? trim($merged['given_name']) : '';
        $middle = isset($merged['middle_name']) && is_string($merged['middle_name']) ? trim($merged['middle_name']) : '';

        $parts = array_filter([$family, $given, $middle], static fn ($x) => $x !== '');
        if ($parts !== []) {
            return implode(' ', $parts);
        }

        if (isset($merged['name']) && is_string($merged['name'])) {
            $n = trim($merged['name']);
            if ($n !== '') {
                return $n;
            }
        }

        return 'Клиент Сбер ID';
    }

    /** Даты Сбера: YYYY-MM-DD или DD.MM.YYYY (instruction userinfo-пример для birthdate). */
    private static function parseSberFlexibleDate(string $raw): ?\DateTimeImmutable
    {
        if ($raw === '') {
            return null;
        }
        if (preg_match('{^\d{4}-\d{2}-\d{2}$}', $raw) === 1) {
            $d = \DateTimeImmutable::createFromFormat('Y-m-d', $raw);

            return $d instanceof \DateTimeImmutable ? $d : null;
        }
        if (preg_match('{^\d{2}\.\d{2}\.\d{4}$}', $raw) === 1) {
            $d = \DateTimeImmutable::createFromFormat('d.m.Y', $raw);

            return $d instanceof \DateTimeImmutable ? $d : null;
        }

        try {
            return new \DateTimeImmutable($raw);
        } catch (\Exception) {
            return null;
        }
    }

    /** Сбер: 1 / 2; CRM ожидает M / F (@see templates/admin/client_show.html.twig). */
    private static function normalizeSberGender(mixed $g): ?string
    {
        if ($g === 1 || $g === '1') {
            return 'M';
        }
        if ($g === 2 || $g === '2') {
            return 'F';
        }
        if (!is_string($g)) {
            return null;
        }

        $s = strtolower(trim($g));
        if ($s === '') {
            return null;
        }

        return match ($s) {
            'male', 'm' => 'M',
            'female', 'f' => 'F',
            default => match (true) {
                str_contains($s, 'жен') || str_starts_with($s, 'ж') => 'F',
                str_contains($s, 'муж') || str_starts_with($s, 'м') => 'M',
                default => null,
            },
        };
    }
    private function formatRussianPhoneDigits(string $raw): string
    {
        $digits = preg_replace('/\D+/', '', $raw) ?? '';
        if ($digits === '') {
            return $raw;
        }
        if (strlen($digits) === 10) {
            $digits = '7' . $digits;
        }
        if (strlen($digits) === 11 && $digits[0] === '8') {
            $digits = '7' . substr($digits, 1);
        }
        if (strlen($digits) !== 11 || $digits[0] !== '7') {
            return $raw;
        }
        $n = substr($digits, 1);

        return sprintf('+7 %s %s-%s-%s', substr($n, 0, 3), substr($n, 3, 3), substr($n, 6, 2), substr($n, 8, 2));
    }
}
