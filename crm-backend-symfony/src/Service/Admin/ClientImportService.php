<?php

namespace App\Service\Admin;

use App\Entity\Booking;
use App\Entity\Club;
use App\Entity\Subscription;
use App\Entity\SubscriptionPlan;
use App\Entity\Training;
use App\Entity\User;
use App\Service\Api\SubscriptionFreezePolicy;
use Doctrine\ORM\EntityManagerInterface;
use PhpOffice\PhpSpreadsheet\IOFactory;
use PhpOffice\PhpSpreadsheet\Shared\Date as ExcelDate;
use Symfony\Component\HttpFoundation\File\UploadedFile;

/**
 * Импорт из CSV/XLSX: клиенты (User), абонементы (Subscription), записи (Booking).
 *
 * Excel: несколько листов — по имени листа («Клиенты», «Абонементы», «Записи») или колонка «тип».
 * CSV: одна таблица — колонка «тип» / record_type: client | subscription | booking (или клиент | абонемент | запись).
 */
final class ClientImportService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionFreezePolicy $freezePolicy,
    ) {
    }

    public function import(UploadedFile $file, bool $updateExisting): ClientImportResult
    {
        $ext = strtolower($file->getClientOriginalExtension() ?: '');
        $mime = (string) $file->getMimeType();
        if ($ext === '' && str_contains($mime, 'spreadsheet')) {
            $ext = 'xlsx';
        }

        $blocks = match (true) {
            \in_array($ext, ['csv', 'txt'], true) => [
                ['title' => '', 'hint' => null, 'rows' => $this->readCsv($file->getPathname())],
            ],
            \in_array($ext, ['xlsx', 'xls'], true) => $this->readSpreadsheetBlocks($file->getPathname()),
            default => throw new \InvalidArgumentException('Допустимые форматы: .csv, .xlsx, .xls'),
        };

        if ($blocks === []) {
            return new ClientImportResult(0, 0, 0, 0, 0, 0, 0, ['Файл пустой.']);
        }

        /** @var list<array{kind: string, data: array<string, mixed>, ref: string}> $clientJobs */
        $clientJobs = [];
        /** @var list<array{data: array<string, mixed>, ref: string}> $subJobs */
        $subJobs = [];
        /** @var list<array{data: array<string, mixed>, ref: string}> $bookJobs */
        $bookJobs = [];
        $errors = [];

        foreach ($blocks as $block) {
            $rows = $block['rows'];
            if ($rows === []) {
                continue;
            }
            $headerRow = array_shift($rows);
            $columnMap = $this->buildColumnMap($headerRow);
            if (!$this->blockHasRecognizedColumns($columnMap, $block['hint'])) {
                $errors[] = 'Лист «' . $block['title'] . '»: не распознаны заголовки — пропуск.';

                continue;
            }

            $rowNum = 1;
            foreach ($rows as $row) {
                ++$rowNum;
                if (!$this->rowHasAnyValue($row)) {
                    continue;
                }
                $data = $this->extractRow($row, $columnMap);
                $kind = $this->resolveRowKind($data, $block['hint']);
                $ref = ($block['title'] !== '' ? $block['title'] . ', строка ' : 'строка ') . $rowNum;

                try {
                    if ($kind === 'client') {
                        $clientJobs[] = ['kind' => 'client', 'data' => $data, 'ref' => $ref];
                    } elseif ($kind === 'subscription') {
                        $subJobs[] = ['data' => $data, 'ref' => $ref];
                    } else {
                        $bookJobs[] = ['data' => $data, 'ref' => $ref];
                    }
                } catch (\Throwable $e) {
                    $errors[] = $ref . ': ' . $e->getMessage();
                }
            }
        }

        $clientsCreated = 0;
        $clientsUpdated = 0;
        $subsCreated = 0;
        $subsUpdated = 0;
        $booksCreated = 0;
        $booksUpdated = 0;
        $skipped = 0;

        foreach ($clientJobs as $job) {
            try {
                $r = $this->importOneClientRow($job['data'], $updateExisting);
                if ($r === 'created') {
                    ++$clientsCreated;
                } elseif ($r === 'updated') {
                    ++$clientsUpdated;
                } else {
                    ++$skipped;
                }
            } catch (\Throwable $e) {
                $errors[] = $job['ref'] . ': ' . $e->getMessage();
                ++$skipped;
            }
        }

        $this->em->flush();

        foreach ($subJobs as $job) {
            try {
                $r = $this->importSubscriptionRow($job['data'], $updateExisting);
                if ($r === 'created') {
                    ++$subsCreated;
                } elseif ($r === 'updated') {
                    ++$subsUpdated;
                } else {
                    ++$skipped;
                }
            } catch (\Throwable $e) {
                $errors[] = $job['ref'] . ': ' . $e->getMessage();
                ++$skipped;
            }
        }

        foreach ($bookJobs as $job) {
            try {
                $r = $this->importBookingRow($job['data'], $updateExisting);
                if ($r === 'created') {
                    ++$booksCreated;
                } elseif ($r === 'updated') {
                    ++$booksUpdated;
                } else {
                    ++$skipped;
                }
            } catch (\Throwable $e) {
                $errors[] = $job['ref'] . ': ' . $e->getMessage();
                ++$skipped;
            }
        }

        $this->em->flush();

        return new ClientImportResult(
            $clientsCreated,
            $clientsUpdated,
            $subsCreated,
            $subsUpdated,
            $booksCreated,
            $booksUpdated,
            $skipped,
            $errors
        );
    }

    /**
     * @param array<int, string> $columnMap
     */
    private function blockHasRecognizedColumns(array $columnMap, ?string $hint): bool
    {
        $fields = array_values(array_filter($columnMap, fn (string $f) => $f !== '_ignore'));
        if ($fields === []) {
            return false;
        }
        $clientKeys = ['name', 'email', 'phone', 'id', 'record_type'];
        $subKeys = ['plan_id', 'plan_name', 'subscription_start', 'client_id', 'client_email', 'client_phone', 'record_type'];
        $bookKeys = ['training_id', 'training_start', 'client_id', 'client_email', 'client_phone', 'record_type'];

        $hasClient = array_intersect($fields, $clientKeys) !== [];
        $hasSub = array_intersect($fields, $subKeys) !== [];
        $hasBook = array_intersect($fields, $bookKeys) !== [];

        if ($hint === 'subscription') {
            return $hasSub || \in_array('record_type', $fields, true);
        }
        if ($hint === 'booking') {
            return $hasBook || \in_array('record_type', $fields, true);
        }
        if ($hint === 'client') {
            return $hasClient;
        }

        return $hasClient || $hasSub || $hasBook;
    }

    /**
     * @return list<array{title: string, hint: ?string, rows: list<list<mixed>>}>
     */
    private function readSpreadsheetBlocks(string $path): array
    {
        $spreadsheet = IOFactory::load($path);
        $blocks = [];
        for ($si = 0; $si < $spreadsheet->getSheetCount(); ++$si) {
            $sheet = $spreadsheet->getSheet($si);
            $title = $sheet->getTitle();
            $lower = mb_strtolower($title);
            $rows = $sheet->toArray('', true, true, false);
            $out = [];
            foreach ($rows as $row) {
                $out[] = array_map(fn ($c) => is_string($c) ? trim($c) : $c, $row);
            }
            if ($out === []) {
                continue;
            }
            $hint = match (true) {
                str_contains($lower, 'абонем') || str_contains($lower, 'subscription') => 'subscription',
                str_contains($lower, 'запис') || str_contains($lower, 'booking') => 'booking',
                str_contains($lower, 'клиент') || str_contains($lower, 'client') || str_contains($lower, 'users') || str_contains($lower, 'people') => 'client',
                default => null,
            };
            $blocks[] = ['title' => $title, 'hint' => $hint, 'rows' => $out];
        }

        return $blocks;
    }

    /**
     * @return list<list<mixed>>
     */
    private function readCsv(string $path): array
    {
        $raw = file_get_contents($path);
        if ($raw === false) {
            throw new \RuntimeException('Не удалось прочитать файл.');
        }
        if (str_starts_with($raw, "\xEF\xBB\xBF")) {
            $raw = substr($raw, 3);
        }
        $firstLine = preg_split('/\r\n|\r|\n/', $raw, 2)[0] ?? '';
        $delim = ';';
        if ($firstLine !== '' && substr_count($firstLine, ',') > substr_count($firstLine, ';')) {
            $delim = ',';
        }
        $handle = fopen('php://memory', 'r+b');
        if ($handle === false) {
            throw new \RuntimeException('Не удалось открыть буфер для CSV.');
        }
        fwrite($handle, $raw);
        rewind($handle);
        $rows = [];
        while (($row = fgetcsv($handle, 0, $delim)) !== false) {
            $rows[] = array_map(fn ($c) => is_string($c) ? trim($c) : $c, $row);
        }
        fclose($handle);

        return $rows;
    }

    /**
     * @param list<mixed> $headerRow
     * @return array<int, string>
     */
    private function buildColumnMap(array $headerRow): array
    {
        $map = [];
        foreach ($headerRow as $i => $header) {
            $key = $this->mapHeaderToField((string) $header);
            if ($key !== null) {
                $map[(int) $i] = $key;
            }
        }

        return $map;
    }

    private function mapHeaderToField(string $header): ?string
    {
        $h = mb_strtolower(str_replace(["\xc2\xa0", "\xe2\x80\xaf"], [' ', ' '], trim($header)));
        $aliases = [
            'тип' => 'record_type',
            'тип строки' => 'record_type',
            'вид' => 'record_type',
            'record_type' => 'record_type',
            'тип записи' => 'record_type',

            'id' => 'id',
            'имя' => 'name',
            'name' => 'name',
            'фио' => 'name',
            'fullname' => 'name',
            'full_name' => 'name',
            'email' => 'email',
            'e-mail' => 'email',
            'почта' => 'email',
            'mail' => 'email',
            'телефон' => 'phone',
            'phone' => 'phone',
            'mobile' => 'phone',
            'тел' => 'phone',
            'дата рождения' => 'date_of_birth',
            'дата_рождения' => 'date_of_birth',
            'birthday' => 'date_of_birth',
            'date_of_birth' => 'date_of_birth',
            'дата рожд' => 'date_of_birth',
            'паспорт' => 'passport_combined',
            'passport' => 'passport_combined',
            'серия паспорта' => 'passport_series',
            'passport_series' => 'passport_series',
            'серия' => 'passport_series',
            'номер паспорта' => 'passport_number',
            'passport_number' => 'passport_number',
            'номер' => 'passport_number',
            'бонусы' => 'bonus_points',
            'bonus' => 'bonus_points',
            'bonus_points' => 'bonus_points',
            'пол' => 'gender',
            'gender' => 'gender',
            'sex' => 'gender',
            'место рождения' => 'place_of_birth',
            'place_of_birth' => 'place_of_birth',
            'кем выдан' => 'passport_issued_by',
            'passport_issued_by' => 'passport_issued_by',
            'дата выдачи' => 'passport_issue_date',
            'passport_issue_date' => 'passport_issue_date',
            'код подразделения' => 'passport_department_code',
            'passport_department_code' => 'passport_department_code',
            'адрес регистрации' => 'registration_address',
            'registration_address' => 'registration_address',
            'контакт для экстренной связи' => 'emergency_contact',
            'emergency_contact' => 'emergency_contact',
            'создан' => '_ignore',
            'created' => '_ignore',
            'created_at' => '_ignore',

            'id клиента' => 'client_id',
            'client_id' => 'client_id',
            'email клиента' => 'client_email',
            'client_email' => 'client_email',
            'телефон клиента' => 'client_phone',
            'client_phone' => 'client_phone',

            'id абонемента' => 'subscription_id',
            'subscription_id' => 'subscription_id',
            'id тарифа' => 'plan_id',
            'тариф id' => 'plan_id',
            'plan_id' => 'plan_id',
            'id плана' => 'plan_id',
            'название тарифа' => 'plan_name',
            'тариф' => 'plan_name',
            'plan_name' => 'plan_name',
            'начало абонемента' => 'subscription_start',
            'дата начала абонемента' => 'subscription_start',
            'subscription_start' => 'subscription_start',
            'окончание абонемента' => 'subscription_end',
            'дата окончания абонемента' => 'subscription_end',
            'subscription_end' => 'subscription_end',
            'статус абонемента' => 'subscription_status',
            'subscription_status' => 'subscription_status',
            'всего посещений' => 'visits_total',
            'visits_total' => 'visits_total',
            'использовано посещений' => 'visits_used',
            'visits_used' => 'visits_used',
            'дней заморозки всего' => 'freeze_days_total',
            'freeze_days_total' => 'freeze_days_total',
            'дней заморозки использовано' => 'freeze_days_used',
            'freeze_days_used' => 'freeze_days_used',

            'id тренировки' => 'training_id',
            'training_id' => 'training_id',
            'дата и время тренировки' => 'training_start',
            'начало тренировки' => 'training_start',
            'training_start' => 'training_start',
            'название тренировки' => 'training_name',
            'training_name' => 'training_name',
            'тренировка' => 'training_name',
            'статус записи' => 'booking_status',
            'booking_status' => 'booking_status',
            'дата записи' => 'booked_at',
            'booked_at' => 'booked_at',
            'имя на записи' => 'booking_client_name',
            'booking_client_name' => 'booking_client_name',
        ];

        return $aliases[$h] ?? null;
    }

    /**
     * @param list<mixed> $row
     * @param array<int, string> $columnMap
     * @return array<string, mixed>
     */
    private function extractRow(array $row, array $columnMap): array
    {
        $data = [];
        foreach ($columnMap as $colIndex => $field) {
            if ($field === '_ignore') {
                continue;
            }
            $data[$field] = $row[$colIndex] ?? '';
        }

        return $data;
    }

    /** @param list<mixed> $row */
    private function rowHasAnyValue(array $row): bool
    {
        foreach ($row as $cell) {
            if ($cell !== null && $cell !== '' && (string) $cell !== '—') {
                return true;
            }
        }

        return false;
    }

    /** @param array<string, mixed> $data */
    private function resolveRowKind(array $data, ?string $hint): string
    {
        $raw = isset($data['record_type']) ? mb_strtolower(trim((string) $data['record_type'])) : '';
        $map = [
            'client' => 'client',
            'клиент' => 'client',
            'subscription' => 'subscription',
            'абонемент' => 'subscription',
            'абонементы' => 'subscription',
            'booking' => 'booking',
            'запись' => 'booking',
            'записи' => 'booking',
        ];
        if ($raw !== '' && isset($map[$raw])) {
            return $map[$raw];
        }
        if ($hint === 'subscription') {
            return 'subscription';
        }
        if ($hint === 'booking') {
            return 'booking';
        }
        if ($hint === 'client') {
            return 'client';
        }

        return 'client';
    }

    /**
     * @param array<string, mixed> $data
     */
    private function importOneClientRow(array $data, bool $updateExisting): string
    {
        $name = trim((string) ($data['name'] ?? ''));
        $email = trim((string) ($data['email'] ?? ''));
        $phoneRaw = trim((string) ($data['phone'] ?? ''));
        $phone = $this->formatPhoneForDb($phoneRaw);

        if ($name === '' && $email === '' && $phone === '') {
            return 'skip';
        }

        $user = null;
        $idRaw = $data['id'] ?? null;
        if ($idRaw !== null && $idRaw !== '' && is_numeric($idRaw)) {
            $user = $this->em->getRepository(User::class)->find((int) $idRaw);
        }
        if (!$user && $email !== '' && filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $user = $this->em->getRepository(User::class)->findOneBy(['email' => mb_strtolower($email)]);
        }
        if (!$user && $phone !== '') {
            $user = $this->findUserByLoosePhone($phoneRaw);
        }

        if ($user) {
            if (!$updateExisting) {
                return 'skip';
            }
            $this->applyDataToUser($user, $data, $phone ?: $user->getPhone(), false);
            $this->em->persist($user);

            return 'updated';
        }

        $finalEmail = $this->allocateUniqueEmail($email, $phoneRaw);
        if ($finalEmail === null) {
            throw new \RuntimeException('Email уже занят другим клиентом; укажите другой или обновите существующую запись.');
        }
        if ($phone === '') {
            throw new \RuntimeException('Для нового клиента нужен телефон или существующий ID/email.');
        }
        if ($name === '') {
            $name = $this->nameFromEmail($finalEmail);
        }

        $new = (new User())
            ->setName(mb_substr($name, 0, 100))
            ->setEmail(mb_substr(mb_strtolower($finalEmail), 0, 180))
            ->setPhone(mb_substr($phone, 0, 32));

        $this->applyDataToUser($new, $data, $phone, true);
        $this->em->persist($new);

        return 'created';
    }

    /**
     * @param array<string, mixed> $data
     */
    private function importSubscriptionRow(array $data, bool $updateExisting): string
    {
        $user = $this->resolveUserForSubBooking($data);
        if (!$user) {
            throw new \RuntimeException('Клиент не найден (нужен ID клиента, email или телефон).');
        }

        $plan = $this->resolvePlan($data);
        if (!$plan) {
            throw new \RuntimeException('Тариф не найден (колонки «ID тарифа» или «Название тарифа»).');
        }

        $start = $this->parseDateField($data['subscription_start'] ?? null);
        if ($start === null) {
            throw new \RuntimeException('Не указана дата начала абонемента.');
        }

        $end = $this->parseDateField($data['subscription_end'] ?? null);
        $status = $this->normalizeSubscriptionStatus($data['subscription_status'] ?? 'active');

        $subIdRaw = $data['subscription_id'] ?? null;
        if ($subIdRaw !== null && $subIdRaw !== '' && is_numeric($subIdRaw)) {
            $sub = $this->em->getRepository(Subscription::class)->find((int) $subIdRaw);
            if ($sub && $sub->getUser()->getId() === $user->getId()) {
                if (!$updateExisting) {
                    return 'skip';
                }
                $this->applySubscriptionFields($sub, $plan, $start, $end, $status, $data);
                $this->em->persist($sub);

                return 'updated';
            }
        }

        $existing = $this->em->getRepository(Subscription::class)->findOneBy([
            'user' => $user,
            'plan' => $plan,
            'startDate' => $start,
        ]);

        if ($existing) {
            if (!$updateExisting) {
                return 'skip';
            }
            $this->applySubscriptionFields($existing, $plan, $start, $end, $status, $data);
            $this->em->persist($existing);

            return 'updated';
        }

        $sub = new Subscription();
        $sub->setUser($user);
        $this->applySubscriptionFields($sub, $plan, $start, $end, $status, $data);
        $this->em->persist($sub);

        return 'created';
    }

    /**
     * @param array<string, mixed> $data
     */
    private function importBookingRow(array $data, bool $updateExisting): string
    {
        $user = $this->resolveUserForSubBooking($data);
        if (!$user) {
            throw new \RuntimeException('Клиент не найден (нужен ID клиента, email или телефон).');
        }

        $training = $this->resolveTraining($data);
        if (!$training) {
            throw new \RuntimeException('Тренировка не найдена (ID тренировки или дата/время начала ±90 сек и при необходимости название).');
        }

        $status = $this->normalizeBookingStatus($data['booking_status'] ?? 'confirmed');
        $clientName = trim((string) ($data['booking_client_name'] ?? ''));
        if ($clientName === '') {
            $clientName = $user->getName();
        }
        $clientName = mb_substr($clientName, 0, 150);

        $bookedAt = $this->parseDateTimeField($data['booked_at'] ?? null) ?? new \DateTimeImmutable();

        $conflict = $this->findNonCancelledBooking($user, $training);
        if ($conflict) {
            if (!$updateExisting) {
                return 'skip';
            }
            $oldStatus = $conflict->getStatus();
            if ($oldStatus === 'confirmed' && $status !== 'confirmed') {
                $training->setCurrentParticipants(max(0, $training->getCurrentParticipants() - 1));
                $this->em->persist($training);
            }
            if ($oldStatus !== 'confirmed' && $status === 'confirmed') {
                $training->setCurrentParticipants($training->getCurrentParticipants() + 1);
                $this->em->persist($training);
            }
            $conflict->setStatus($status)->setClientName($clientName)->setBookedAt($bookedAt);
            $this->em->persist($conflict);

            return 'updated';
        }

        $booking = (new Booking())
            ->setTraining($training)
            ->setUser($user)
            ->setClientName($clientName)
            ->setStatus($status)
            ->setBookedAt($bookedAt);

        if ($status === 'confirmed') {
            $training->setCurrentParticipants($training->getCurrentParticipants() + 1);
            $this->em->persist($training);
        }

        $this->em->persist($booking);

        return 'created';
    }

    private function findNonCancelledBooking(User $user, Training $training): ?Booking
    {
        return $this->em->createQueryBuilder()
            ->select('b')
            ->from(Booking::class, 'b')
            ->where('b.user = :u AND b.training = :t AND b.status != :c')
            ->setParameter('u', $user)
            ->setParameter('t', $training)
            ->setParameter('c', 'cancelled')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    /**
     * @param array<string, mixed> $data
     */
    private function resolveUserForSubBooking(array $data): ?User
    {
        $cid = trim((string) ($data['client_id'] ?? ''));
        if ($cid !== '' && ctype_digit($cid)) {
            $u = $this->em->getRepository(User::class)->find((int) $cid);
            if ($u) {
                return $u;
            }
        }
        $em = trim((string) ($data['client_email'] ?? ''));
        if ($em !== '' && filter_var($em, FILTER_VALIDATE_EMAIL)) {
            $u = $this->em->getRepository(User::class)->findOneBy(['email' => mb_strtolower($em)]);
            if ($u) {
                return $u;
            }
        }
        $ph = trim((string) ($data['client_phone'] ?? ''));
        if ($ph !== '') {
            $u = $this->findUserByLoosePhone($ph);
            if ($u) {
                return $u;
            }
        }

        $em2 = trim((string) ($data['email'] ?? ''));
        if ($em2 !== '' && filter_var($em2, FILTER_VALIDATE_EMAIL)) {
            $u = $this->em->getRepository(User::class)->findOneBy(['email' => mb_strtolower($em2)]);
            if ($u) {
                return $u;
            }
        }
        $ph2 = trim((string) ($data['phone'] ?? ''));
        if ($ph2 !== '') {
            return $this->findUserByLoosePhone($ph2);
        }
        $id2 = trim((string) ($data['id'] ?? ''));
        if ($id2 !== '' && ctype_digit($id2)) {
            return $this->em->getRepository(User::class)->find((int) $id2);
        }

        return null;
    }

    /**
     * @param array<string, mixed> $data
     */
    private function resolvePlan(array $data): ?SubscriptionPlan
    {
        $pid = trim((string) ($data['plan_id'] ?? ''));
        if ($pid !== '' && ctype_digit($pid)) {
            $p = $this->em->getRepository(SubscriptionPlan::class)->find((int) $pid);
            if ($p) {
                return $p;
            }
        }
        $pname = trim((string) ($data['plan_name'] ?? ''));
        if ($pname === '') {
            return null;
        }
        $low = mb_strtolower($pname);
        foreach ($this->em->getRepository(SubscriptionPlan::class)->findAll() as $p) {
            if (mb_strtolower($p->getName()) === $low) {
                return $p;
            }
        }

        return null;
    }

    /**
     * @param array<string, mixed> $data
     */
    private function resolveTraining(array $data): ?Training
    {
        $tid = trim((string) ($data['training_id'] ?? ''));
        if ($tid !== '' && ctype_digit($tid)) {
            return $this->em->getRepository(Training::class)->find((int) $tid);
        }

        $start = $this->parseDateTimeField($data['training_start'] ?? null);
        if ($start === null) {
            return null;
        }

        $from = (clone $start)->modify('-90 seconds');
        $to = (clone $start)->modify('+90 seconds');
        $name = trim((string) ($data['training_name'] ?? ''));

        $qb = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Training::class, 't')
            ->where('t.startAt >= :f AND t.startAt <= :t2')
            ->setParameter('f', $from)
            ->setParameter('t2', $to)
            ->setMaxResults(10);

        if ($name !== '') {
            $qb->andWhere('t.name = :n')->setParameter('n', $name);
        }

        $list = $qb->getQuery()->getResult();
        if (\count($list) === 1) {
            return $list[0];
        }
        if (\count($list) > 1) {
            throw new \RuntimeException('Несколько тренировок в этом интервале — укажите «Название тренировки» или ID.');
        }

        return null;
    }

    /**
     * @param array<string, mixed> $data
     */
    private function applySubscriptionFields(
        Subscription $sub,
        SubscriptionPlan $plan,
        \DateTimeImmutable $start,
        ?\DateTimeImmutable $end,
        string $status,
        array $data,
    ): void {
        $sub->setPlan($plan)->setStartDate($start)->setEndDate($end)->setStatus($status);

        if (isset($data['subscription_club_id']) && $data['subscription_club_id'] !== '' && $data['subscription_club_id'] !== '—') {
            $cid = (int) $data['subscription_club_id'];
            $sub->setClub($cid > 0 ? $this->em->getRepository(Club::class)->find($cid) : null);
        }

        if (isset($data['visits_total']) && $data['visits_total'] !== '' && $data['visits_total'] !== '—') {
            $sub->setVisitsTotal(max(0, (int) $data['visits_total']));
        } elseif ($sub->getVisitsTotal() === null && $plan->getVisitsCount() !== null) {
            $sub->setVisitsTotal($plan->getVisitsCount());
        }

        if (isset($data['visits_used']) && $data['visits_used'] !== '' && $data['visits_used'] !== '—') {
            $sub->setVisitsUsed(max(0, (int) $data['visits_used']));
        }

        if (isset($data['freeze_days_total']) && $data['freeze_days_total'] !== '' && $data['freeze_days_total'] !== '—') {
            $sub->setFreezeDaysTotal(max(0, (int) $data['freeze_days_total']));
        } elseif ($sub->getFreezeDaysTotal() === null) {
            $sub->setFreezeDaysTotal($this->freezePolicy->freezeDaysTotalForPlan($plan));
        }

        if (isset($data['freeze_days_used']) && $data['freeze_days_used'] !== '' && $data['freeze_days_used'] !== '—') {
            $sub->setFreezeDaysUsed(max(0, (int) $data['freeze_days_used']));
        }
    }

    private function normalizeSubscriptionStatus(mixed $raw): string
    {
        $s = mb_strtolower(trim((string) $raw));
        if ($s === '' || $s === '—') {
            return 'active';
        }
        $map = [
            'active' => 'active',
            'активен' => 'active',
            'активный' => 'active',
            'frozen' => 'frozen',
            'заморозка' => 'frozen',
            'заморожен' => 'frozen',
            'expired' => 'expired',
            'истёк' => 'expired',
            'истек' => 'expired',
            'истёкший' => 'expired',
        ];

        return $map[$s] ?? 'active';
    }

    private function normalizeBookingStatus(mixed $raw): string
    {
        $s = mb_strtolower(trim((string) $raw));
        if ($s === '' || $s === '—') {
            return 'confirmed';
        }
        $map = [
            'confirmed' => 'confirmed',
            'подтверждена' => 'confirmed',
            'подтверждён' => 'confirmed',
            'waiting' => 'waiting',
            'ожидание' => 'waiting',
            'лист ожидания' => 'waiting',
            'cancelled' => 'cancelled',
            'отмена' => 'cancelled',
            'отменена' => 'cancelled',
            'completed' => 'completed',
            'завершена' => 'completed',
            'посещена' => 'completed',
        ];

        return $map[$s] ?? 'confirmed';
    }

    private function parseDateTimeField(mixed $value): ?\DateTimeImmutable
    {
        if ($value === null || $value === '') {
            return null;
        }
        if (is_numeric($value)) {
            try {
                $dt = ExcelDate::excelToDateTimeObject((float) $value);

                return \DateTimeImmutable::createFromMutable($dt);
            } catch (\Throwable) {
            }
        }
        $v = trim((string) $value);
        if ($v === '' || $v === '—') {
            return null;
        }
        foreach (
            [
                'Y-m-d H:i:s',
                'Y-m-d H:i',
                'd.m.Y H:i:s',
                'd.m.Y H:i',
                'd.m.Y\TH:i:s',
                'Y-m-d\TH:i:s',
            ] as $fmt
        ) {
            $dt = \DateTimeImmutable::createFromFormat('!' . $fmt, $v);
            if ($dt instanceof \DateTimeImmutable) {
                return $dt;
            }
        }

        $d = $this->parseDateField($value);

        return $d?->setTime(0, 0);
    }

    private function allocateUniqueEmail(string $email, string $phoneRaw): ?string
    {
        if ($email !== '' && filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $low = mb_strtolower($email);
            $exists = $this->em->getRepository(User::class)->findOneBy(['email' => $low]);

            return $exists ? null : $low;
        }

        $digits = preg_replace('/\D+/', '', $phoneRaw) ?: 'nophone';
        $tail = mb_substr($digits, -10);
        $candidate = 'import.p' . $tail . '@clients.import.local';
        $n = 0;
        while ($this->em->getRepository(User::class)->findOneBy(['email' => $candidate])) {
            $candidate = 'import.p' . $tail . '.' . (++$n) . '@clients.import.local';
            if ($n > 5000) {
                return null;
            }
        }

        return mb_substr($candidate, 0, 180);
    }

    private function nameFromEmail(string $email): string
    {
        $local = explode('@', $email, 2)[0];

        return mb_substr($local !== '' ? $local : 'Клиент', 0, 100);
    }

    /**
     * @param array<string, mixed> $data
     */
    private function applyDataToUser(User $user, array $data, string $phoneForDb, bool $isNew): void
    {
        if (isset($data['name'])) {
            $n = trim((string) $data['name']);
            if ($n !== '') {
                $user->setName(mb_substr($n, 0, 100));
            }
        }

        if (!$isNew && isset($data['email'])) {
            $e = trim((string) $data['email']);
            if ($e !== '' && filter_var($e, FILTER_VALIDATE_EMAIL)) {
                $low = mb_strtolower($e);
                $other = $this->em->getRepository(User::class)->findOneBy(['email' => $low]);
                if ($other !== null && $other->getId() !== $user->getId()) {
                    throw new \RuntimeException('Email «' . $e . '» уже занят другим клиентом.');
                }
                $user->setEmail(mb_substr($low, 0, 180));
            }
        }

        if ($phoneForDb !== '') {
            $user->setPhone(mb_substr($phoneForDb, 0, 32));
        }

        if (isset($data['bonus_points']) && $data['bonus_points'] !== '' && $data['bonus_points'] !== '—') {
            $user->setBonusPoints(max(0, (int) $data['bonus_points']));
        }

        $gender = $this->normalizeGender($data['gender'] ?? null);
        if ($gender !== null) {
            $user->setGender($gender);
        }

        if (isset($data['date_of_birth'])) {
            $dob = $this->parseDateField($data['date_of_birth']);
            if ($dob !== null) {
                $user->setDateOfBirth($dob);
            }
        }

        if (isset($data['place_of_birth'])) {
            $v = trim((string) $data['place_of_birth']);
            $user->setPlaceOfBirth($v !== '' && $v !== '—' ? mb_substr($v, 0, 255) : null);
        }

        $series = isset($data['passport_series']) ? trim((string) $data['passport_series']) : '';
        $number = isset($data['passport_number']) ? trim((string) $data['passport_number']) : '';
        $combined = isset($data['passport_combined']) ? trim((string) $data['passport_combined']) : '';

        if ($combined !== '' && $combined !== '—' && $series === '' && $number === '') {
            if (preg_match('/^(\d{4})\s+(\d{6})$/u', $combined, $m)) {
                $series = $m[1];
                $number = $m[2];
            } elseif (preg_match('/^(\d{4})(\d{6})$/u', preg_replace('/\s+/', '', $combined), $m)) {
                $series = $m[1];
                $number = $m[2];
            }
        }

        if ($series !== '') {
            $user->setPassportSeries(mb_substr($series, 0, 10));
        }
        if ($number !== '') {
            $user->setPassportNumber(mb_substr($number, 0, 10));
        }

        foreach (
            [
                'passport_issued_by' => fn (string $v) => $user->setPassportIssuedBy(mb_substr($v, 0, 255)),
                'passport_department_code' => fn (string $v) => $user->setPassportDepartmentCode(mb_substr($v, 0, 10)),
                'registration_address' => fn (string $v) => $user->setRegistrationAddress(mb_substr($v, 0, 255)),
                'emergency_contact' => fn (string $v) => $user->setEmergencyContact(mb_substr($v, 0, 255)),
            ] as $key => $setter
        ) {
            if (!isset($data[$key])) {
                continue;
            }
            $v = trim((string) $data[$key]);
            if ($v === '' || $v === '—') {
                continue;
            }
            $setter($v);
        }

        if (isset($data['passport_issue_date'])) {
            $pid = $this->parseDateField($data['passport_issue_date']);
            if ($pid !== null) {
                $user->setPassportIssueDate($pid);
            }
        }
    }

    private function normalizeGender(mixed $raw): ?string
    {
        if ($raw === null || $raw === '') {
            return null;
        }
        $g = mb_strtolower(trim((string) $raw));
        if (\in_array($g, ['m', 'м', 'male', 'муж', 'мужской'], true)) {
            return 'M';
        }
        if (\in_array($g, ['f', 'ж', 'female', 'жен', 'женский'], true)) {
            return 'F';
        }

        return null;
    }

    private function parseDateField(mixed $value): ?\DateTimeImmutable
    {
        if ($value === null || $value === '') {
            return null;
        }
        if (is_numeric($value)) {
            try {
                $dt = ExcelDate::excelToDateTimeObject((float) $value);

                return \DateTimeImmutable::createFromMutable($dt);
            } catch (\Throwable) {
            }
        }
        $v = trim((string) $value);
        if ($v === '' || $v === '—') {
            return null;
        }
        foreach (['Y-m-d', 'd.m.Y', 'd/m/Y', 'm/d/Y'] as $fmt) {
            $dt = \DateTimeImmutable::createFromFormat('!' . $fmt, $v);
            if ($dt instanceof \DateTimeImmutable) {
                return $dt;
            }
        }
        try {
            return new \DateTimeImmutable($v);
        } catch (\Throwable) {
            return null;
        }
    }

    private function formatPhoneForDb(string $raw): string
    {
        $d = preg_replace('/\D+/', '', $raw);
        if ($d === '') {
            return '';
        }
        if (strlen($d) === 11 && $d[0] === '8') {
            $d = '7' . substr($d, 1);
        }
        if (strlen($d) === 11 && $d[0] === '7') {
            return '+' . $d;
        }
        if (strlen($d) === 10) {
            return '+7' . $d;
        }

        return '+' . $d;
    }

    private function findUserByLoosePhone(string $raw): ?User
    {
        $digits = preg_replace('/\D+/', '', $raw);
        if (strlen($digits) < 10) {
            return null;
        }
        $last10 = substr($digits, -10);
        $users = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('u.phone LIKE :tail')
            ->setParameter('tail', '%' . $last10)
            ->setMaxResults(20)
            ->getQuery()
            ->getResult();

        $matches = [];
        foreach ($users as $u) {
            $ud = preg_replace('/\D+/', '', $u->getPhone());
            if ($ud === $digits || str_ends_with($ud, $last10)) {
                $matches[] = $u;
            }
        }

        if (count($matches) === 1) {
            return $matches[0];
        }

        return null;
    }
}
