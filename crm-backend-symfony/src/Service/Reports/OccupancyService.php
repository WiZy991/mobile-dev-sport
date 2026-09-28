<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\StaffUser;
use App\Entity\User;
use App\Service\ClubTimezone;
use Doctrine\DBAL\Connection;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\Cache\CacheInterface;
use Symfony\Contracts\Cache\ItemInterface;

/**
 * «Сколько людей сейчас в зале» и кто именно (клиенты + тренеры из приложения специалиста).
 *
 * Логика: за окно присутствия для каждого посетителя берём ПОСЛЕДНЕЕ granted-событие.
 * Если оно entry — человек в зале; если exit — вышел.
 *
 * Окно суток: с 23:15 до 23:15 по Владивостоку (не полночь UTC и не 23:15 UTC).
 * Максимум в зале: 3 часа с последнего входа (потом авто-exit в лог; QR-выход не трогаем).
 *
 * access_logs.created_at — UTC; границы окна переводятся в UTC для SQL.
 */
final class OccupancyService
{
    private const COUNT_CACHE_TTL_SECONDS = 5;

    /** Сброс «суток зала» по местному времени клуба (Владивосток). */
    public const DAY_RESET_HOUR = 23;
    public const DAY_RESET_MINUTE = 15;

    /** Автоматический выход, если не было QR-выхода. */
    public const MAX_STAY_SECONDS = 3 * 3600;

    /** Кэш на запрос: сеть из одного зала или из нескольких. */
    private ?bool $singleClubNetwork = null;

    /** Есть ли access_logs.staff_user_id (после миграции). */
    private ?bool $hasStaffUserColumn = null;

    public function __construct(
        private readonly EntityManagerInterface $em,
        #[Autowire(service: 'cache.app')]
        private readonly CacheInterface $cache,
    ) {
    }

    private function accessLogsHaveStaffUser(): bool
    {
        if ($this->hasStaffUserColumn !== null) {
            return $this->hasStaffUserColumn;
        }
        try {
            $cols = $this->connection()->createSchemaManager()->listTableColumns('access_logs');
            $this->hasStaffUserColumn = isset($cols['staff_user_id']) || isset($cols['STAFF_USER_ID']);
        } catch (\Throwable) {
            $this->hasStaffUserColumn = false;
        }

        return $this->hasStaffUserColumn;
    }

    /**
     * Окно присутствия: [прошлые 23:15; следующие 23:15) Владивосток → UTC для БД.
     *
     * @return array{from: string, to: string}
     */
    private function presenceWindow(): array
    {
        $clubTz = ClubTimezone::zone();
        $utc = new \DateTimeZone('UTC');
        $now = new \DateTimeImmutable('now', $clubTz);
        $resetToday = $now->setTime(self::DAY_RESET_HOUR, self::DAY_RESET_MINUTE, 0);

        if ($now < $resetToday) {
            $fromLocal = $resetToday->modify('-1 day');
            $toLocal = $resetToday;
        } else {
            $fromLocal = $resetToday;
            $toLocal = $resetToday->modify('+1 day');
        }

        return [
            'from' => $fromLocal->setTimezone($utc)->format('Y-m-d H:i:s'),
            'to' => $toLocal->setTimezone($utc)->format('Y-m-d H:i:s'),
        ];
    }

    /** Нижняя граница «свежего» входа (сейчас − 3 часа), UTC. */
    private function minFreshEntryAtUtc(): string
    {
        return (new \DateTimeImmutable('now', new \DateTimeZone('UTC')))
            ->modify('-' . self::MAX_STAY_SECONDS . ' seconds')
            ->format('Y-m-d H:i:s');
    }

    private function entryStillFresh(string $createdAtUtc): bool
    {
        $at = new \DateTimeImmutable($createdAtUtc, new \DateTimeZone('UTC'));
        $now = new \DateTimeImmutable('now', new \DateTimeZone('UTC'));

        return ($now->getTimestamp() - $at->getTimestamp()) < self::MAX_STAY_SECONDS;
    }

    /**
     * Фильтр клуба.
     *
     * События без клуба (старые логи, шлюз без привязки) относим к залу только в сети
     * из одного зала. В сети из нескольких залов такие события нельзя считать «своими»:
     * иначе счётчик каждого зала показывал бы посетителей всех залов сразу.
     *
     * @return array{sql: string, params: array<string, mixed>}
     */
    private function clubScopeSql(?Club $club, string $column = 'club_id'): array
    {
        if (!$club instanceof Club) {
            return ['sql' => '', 'params' => []];
        }

        $sql = $this->networkHasSingleClub()
            ? " AND ({$column} IS NULL OR {$column} = :club_id)"
            : " AND {$column} = :club_id";

        return [
            'sql' => $sql,
            'params' => ['club_id' => $club->getId()],
        ];
    }

    /** В сети один зал — событиям без клуба больше некуда относиться. */
    private function networkHasSingleClub(): bool
    {
        if ($this->singleClubNetwork === null) {
            $this->singleClubNetwork = (int) $this->em->getRepository(Club::class)->count([]) <= 1;
        }

        return $this->singleClubNetwork;
    }

    /** Сколько человек сейчас в зале (клиенты + тренеры; без клуба — по всей сети). */
    public function countCurrentlyInside(?Club $club = null): int
    {
        $cacheKey = 'occupancy.count.' . ($club?->getId() ?? 'all');

        return (int) $this->cache->get($cacheKey, function (ItemInterface $item) use ($club): int {
            $item->expiresAfter(self::COUNT_CACHE_TTL_SECONDS);
            $sql = $this->buildCurrentlyInsideSql($club, selectColumns: 'COUNT(*) AS cnt', stayFilter: 'fresh');
            $row = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAssociative();

            return (int) ($row['cnt'] ?? 0);
        });
    }

    /**
     * @return list<array{
     *     kind: 'user'|'staff',
     *     user: ?User,
     *     staff: ?StaffUser,
     *     name: string,
     *     phone: string,
     *     entered_at: \DateTimeImmutable,
     *     club_id: ?int,
     *     club_name: ?string
     * }>
     */
    public function listCurrentlyInside(?Club $club = null, int $limit = 200): array
    {
        $sql = $this->buildCurrentlyInsideSql(
            $club,
            selectColumns: 't.person_kind AS person_kind, t.person_id AS person_id, t.last_at AS entered_at, t.last_club_id AS club_id',
            orderBy: 'entered_at DESC',
            limit: $limit,
            stayFilter: 'fresh',
        );

        $rows = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAllAssociative();
        if ($rows === []) {
            return [];
        }

        $userIds = [];
        $staffIds = [];
        foreach ($rows as $r) {
            $id = (int) ($r['person_id'] ?? 0);
            if ($id <= 0) {
                continue;
            }
            if (($r['person_kind'] ?? '') === 'staff') {
                $staffIds[] = $id;
            } else {
                $userIds[] = $id;
            }
        }
        $userIds = array_values(array_unique($userIds));
        $staffIds = array_values(array_unique($staffIds));

        $usersById = [];
        if ($userIds !== []) {
            /** @var User[] $users */
            $users = $this->em->getRepository(User::class)->findBy(['id' => $userIds]);
            foreach ($users as $u) {
                $usersById[$u->getId()] = $u;
            }
        }
        $staffById = [];
        if ($staffIds !== []) {
            /** @var StaffUser[] $staffList */
            $staffList = $this->em->getRepository(StaffUser::class)->findBy(['id' => $staffIds]);
            foreach ($staffList as $s) {
                $staffById[$s->getId()] = $s;
            }
        }

        $clubIds = [];
        foreach ($rows as $r) {
            $clubRaw = $r['club_id'] ?? null;
            if ($clubRaw !== null && $clubRaw !== '') {
                $clubIds[] = (int) $clubRaw;
            }
        }
        $clubIds = array_values(array_unique($clubIds));
        $clubsById = [];
        if ($clubIds !== []) {
            /** @var Club[] $clubs */
            $clubs = $this->em->getRepository(Club::class)->findBy(['id' => $clubIds]);
            foreach ($clubs as $c) {
                $clubsById[$c->getId()] = $c;
            }
        }

        $result = [];
        foreach ($rows as $r) {
            $kind = ($r['person_kind'] ?? '') === 'staff' ? 'staff' : 'user';
            $personId = (int) ($r['person_id'] ?? 0);
            $user = $kind === 'user' ? ($usersById[$personId] ?? null) : null;
            $staff = $kind === 'staff' ? ($staffById[$personId] ?? null) : null;
            if ($kind === 'user' && !$user instanceof User) {
                continue;
            }
            if ($kind === 'staff' && !$staff instanceof StaffUser) {
                continue;
            }
            $clubRaw = $r['club_id'] ?? null;
            $clubId = $clubRaw !== null && $clubRaw !== '' ? (int) $clubRaw : null;
            $clubEntity = $clubId !== null ? ($clubsById[$clubId] ?? null) : null;
            if ($kind === 'staff' && $staff instanceof StaffUser) {
                $name = trim($staff->getName());
                if ($name === '') {
                    $name = trim($staff->getEmail());
                }
                if ($name === '') {
                    $name = 'Тренер #' . $staff->getId();
                }
                $phone = trim((string) ($staff->getTrainer()?->getPhone() ?? ''));
            } else {
                /** @var User $user */
                $name = trim($user->getName());
                if ($name === '') {
                    $name = 'Клиент #' . $user->getId();
                }
                $phone = trim($user->getPhone());
            }
            $result[] = [
                'kind' => $kind,
                'user' => $user,
                'staff' => $staff,
                'name' => $name,
                'phone' => $phone !== '' ? $phone : '—',
                'entered_at' => new \DateTimeImmutable((string) $r['entered_at'], new \DateTimeZone('UTC')),
                'club_id' => $clubId,
                'club_name' => $clubEntity?->getName(),
            ];
        }

        return $result;
    }

    public function forceExit(User $user, ?Club $club = null, string $reason = 'admin_force_exit'): void
    {
        $log = (new AccessLog())
            ->setUser($user)
            ->setClub($club)
            ->setRawData('ADMIN:FORCE_EXIT:' . $user->getId())
            ->setDeviceId('crm-admin')
            ->setEventType('exit')
            ->setResult('granted')
            ->setReason($reason);

        $this->em->persist($log);
        $this->em->flush();
        $this->invalidateCountCache($club);
    }

    public function forceExitStaff(StaffUser $staff, ?Club $club = null, string $reason = 'admin_force_exit'): void
    {
        $log = (new AccessLog())
            ->setStaffUser($staff)
            ->setClub($club)
            ->setRawData('ADMIN:FORCE_EXIT:STAFF:' . $staff->getId())
            ->setDeviceId('crm-admin')
            ->setEventType('exit')
            ->setResult('granted')
            ->setReason($reason);

        $this->em->persist($log);
        $this->em->flush();
        $this->invalidateCountCache($club);
    }

    /**
     * @return int число созданных exit-событий
     */
    public function forceExitAllCurrentlyInside(?Club $club = null): int
    {
        $inside = $this->listCurrentlyInside($club, 500);
        $count = 0;
        foreach ($inside as $row) {
            $rowClub = $club;
            if ($rowClub === null && $row['club_id'] !== null) {
                $rowClub = $this->em->find(Club::class, $row['club_id']);
            }
            $log = (new AccessLog())
                ->setClub($rowClub instanceof Club ? $rowClub : null)
                ->setDeviceId('crm-admin')
                ->setEventType('exit')
                ->setResult('granted')
                ->setReason('admin_clear_hall');
            if ($row['kind'] === 'staff' && $row['staff'] instanceof StaffUser) {
                $log->setStaffUser($row['staff'])
                    ->setRawData('ADMIN:FORCE_EXIT:STAFF:' . $row['staff']->getId());
            } elseif ($row['user'] instanceof User) {
                $log->setUser($row['user'])
                    ->setRawData('ADMIN:FORCE_EXIT:' . $row['user']->getId());
            } else {
                continue;
            }
            $this->em->persist($log);
            ++$count;
        }
        if ($count > 0) {
            $this->em->flush();
            $this->invalidateCountCache($club);
        }

        return $count;
    }

    /**
     * Авто-выход: последнее событие — entry старше 3 часов (в текущем окне суток).
     * QR-выход / entry toggle не меняются — только дописываем exit в лог.
     *
     * @return int число созданных exit
     */
    public function autoExitStaleInside(?Club $club = null): int
    {
        $sql = $this->buildCurrentlyInsideSql(
            $club,
            selectColumns: 't.person_kind AS person_kind, t.person_id AS person_id, t.last_at AS entered_at, t.last_club_id AS club_id',
            orderBy: 'entered_at ASC',
            limit: 500,
            stayFilter: 'stale',
        );
        $rows = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAllAssociative();
        if ($rows === []) {
            return 0;
        }

        $count = 0;
        foreach ($rows as $r) {
            $kind = ($r['person_kind'] ?? '') === 'staff' ? 'staff' : 'user';
            $personId = (int) ($r['person_id'] ?? 0);
            $rowClub = $club;
            $clubRaw = $r['club_id'] ?? null;
            if ($rowClub === null && $clubRaw !== null && $clubRaw !== '') {
                $rowClub = $this->em->find(Club::class, (int) $clubRaw);
            }
            $log = (new AccessLog())
                ->setClub($rowClub instanceof Club ? $rowClub : null)
                ->setDeviceId('crm-auto-exit')
                ->setEventType('exit')
                ->setResult('granted')
                ->setReason('auto_max_stay_3h');
            if ($kind === 'staff') {
                $staff = $this->em->find(StaffUser::class, $personId);
                if (!$staff instanceof StaffUser) {
                    continue;
                }
                $log->setStaffUser($staff)->setRawData('AUTO:MAX_STAY:STAFF:' . $staff->getId());
            } else {
                $user = $this->em->find(User::class, $personId);
                if (!$user instanceof User) {
                    continue;
                }
                $log->setUser($user)->setRawData('AUTO:MAX_STAY:' . $user->getId());
            }
            $this->em->persist($log);
            ++$count;
        }

        if ($count > 0) {
            $this->em->flush();
            $this->invalidateCountCache($club);
        }

        return $count;
    }

    private function invalidateCountCache(?Club $club = null): void
    {
        try {
            $this->cache->delete('occupancy.count.all');
            if ($club?->getId() !== null) {
                $this->cache->delete('occupancy.count.' . $club->getId());
            }
        } catch (\Throwable) {
        }
    }

    /** Сбросить кэш счётчика после реального входа/выхода (шлюз / QR). */
    public function notifyPresenceChanged(?Club $club = null): void
    {
        $this->invalidateCountCache($club);
    }

    /**
     * @param 'fresh'|'stale'|'any' $stayFilter fresh = ≤3ч; stale = >3ч (для авто-exit); any = без фильтра
     *
     * @return array{sql: string, params: array<string, mixed>}
     */
    private function buildCurrentlyInsideSql(
        ?Club $club,
        string $selectColumns,
        ?string $orderBy = null,
        ?int $limit = null,
        string $stayFilter = 'fresh',
    ): array {
        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        $staySql = '';
        if ($stayFilter === 'fresh' || $stayFilter === 'stale') {
            $params['min_entry'] = $this->minFreshEntryAtUtc();
            $staySql = $stayFilter === 'fresh'
                ? ' AND ranked.created_at >= :min_entry'
                : ' AND ranked.created_at < :min_entry';
        }

        // Клиент (user_id) или тренер (staff_user_id) — отдельные «личности» в зале.
        if ($this->accessLogsHaveStaffUser()) {
            $personKey = "CASE
                WHEN al.user_id IS NOT NULL THEN CONCAT('u', al.user_id)
                ELSE CONCAT('s', al.staff_user_id)
            END";
            $personKind = "CASE WHEN al.user_id IS NOT NULL THEN 'user' ELSE 'staff' END";
            $personId = 'COALESCE(al.user_id, al.staff_user_id)';
            $personFilter = '(al.user_id IS NOT NULL OR al.staff_user_id IS NOT NULL)';
        } else {
            $personKey = "CONCAT('u', al.user_id)";
            $personKind = "'user'";
            $personId = 'al.user_id';
            $personFilter = 'al.user_id IS NOT NULL';
        }

        $inner = "SELECT ranked.person_kind AS person_kind,
                         ranked.person_id AS person_id,
                         ranked.event_type AS last_type,
                         DATE_FORMAT(ranked.created_at, '%Y-%m-%d %H:%i:%s') AS last_at,
                         IFNULL(ranked.club_id, '') AS last_club_id
                  FROM (
                      SELECT {$personKind} AS person_kind,
                             {$personId} AS person_id,
                             al.event_type, al.created_at, al.club_id,
                             ROW_NUMBER() OVER (PARTITION BY {$personKey} ORDER BY al.id DESC) AS rn
                      FROM access_logs al
                      WHERE al.result = 'granted'
                        AND {$personFilter}
                        AND al.created_at >= :from
                        AND al.created_at < :to" . $scope['sql'] . '
                  ) ranked
                  WHERE ranked.rn = 1
                    AND ranked.event_type = \'entry\'' . $staySql;

        $sql = "SELECT $selectColumns FROM ($inner) t";

        if ($orderBy !== null) {
            $sql .= "\n                ORDER BY $orderBy";
        }
        if ($limit !== null && $limit > 0) {
            $sql .= "\n                LIMIT $limit";
        }

        return ['sql' => $sql, 'params' => $params];
    }

    /** Сейчас ли клиент в зале — то же правило, что и счётчик (окно + ≤3 ч). */
    public function isUserCurrentlyInside(User $user, ?Club $club = null): bool
    {
        $row = $this->lastGrantedEventRowForUser($user, $club);
        if ($row === null || ($row['event_type'] ?? '') !== 'entry') {
            return false;
        }

        return $this->entryStillFresh((string) $row['created_at']);
    }

    /** Сейчас ли тренер в зале. */
    public function isStaffCurrentlyInside(StaffUser $staff, ?Club $club = null): bool
    {
        if (!$this->accessLogsHaveStaffUser()) {
            return false;
        }
        $row = $this->lastGrantedEventRowForStaff($staff, $club);
        if ($row === null || ($row['event_type'] ?? '') !== 'entry') {
            return false;
        }

        return $this->entryStillFresh((string) $row['created_at']);
    }

    /**
     * Последнее granted-событие пользователя за окно присутствия.
     *
     * @return array{event_type: string, id: int, created_at: string}|null
     */
    public function lastGrantedEvent(User $user, ?Club $club = null): ?array
    {
        return $this->lastGrantedEventRowForUser($user, $club);
    }

    /**
     * Тот же QR уже успешно обработан недавно (скан + эхо PERCo events) — не писать второй лог.
     *
     * @return array{event_type: string, id: int}|null
     */
    public function findRecentGrantedByRawQr(string $qr, int $withinSeconds = 90): ?array
    {
        $qr = trim($qr);
        if ($qr === '' || $withinSeconds < 1) {
            return null;
        }

        $from = (new \DateTimeImmutable('now', new \DateTimeZone('UTC')))
            ->modify('-' . $withinSeconds . ' seconds')
            ->format('Y-m-d H:i:s');

        $row = $this->connection()->executeQuery(
            'SELECT id, event_type
             FROM access_logs
             WHERE result = \'granted\'
               AND raw_data = :qr
               AND created_at >= :from
             ORDER BY id DESC
             LIMIT 1',
            ['qr' => $qr, 'from' => $from],
        )->fetchAssociative();

        if ($row === false) {
            return null;
        }

        return [
            'id' => (int) $row['id'],
            'event_type' => (string) $row['event_type'],
        ];
    }

    /**
     * Секунды с последнего granted entry (для защиты от мгновенного «выхода» из эха PERCo).
     */
    public function secondsSinceLastGrantedEntry(User $user, ?Club $club = null): ?int
    {
        return $this->secondsSinceLastGrantedEntryForColumn('user_id', (int) $user->getId(), $club);
    }

    public function secondsSinceLastGrantedStaffEntry(StaffUser $staff, ?Club $club = null): ?int
    {
        if (!$this->accessLogsHaveStaffUser()) {
            return null;
        }

        return $this->secondsSinceLastGrantedEntryForColumn('staff_user_id', (int) $staff->getId(), $club);
    }

    private function secondsSinceLastGrantedEntryForColumn(string $column, int $id, ?Club $club): ?int
    {
        if (!\in_array($column, ['user_id', 'staff_user_id'], true) || $id <= 0) {
            return null;
        }

        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'pid' => $id,
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        $row = $this->connection()->executeQuery(
            "SELECT created_at
             FROM access_logs
             WHERE result = 'granted'
               AND event_type = 'entry'
               AND {$column} = :pid
               AND created_at >= :from
               AND created_at < :to" . $scope['sql'] . '
             ORDER BY id DESC
             LIMIT 1',
            $params,
        )->fetchAssociative();

        if ($row === false || empty($row['created_at'])) {
            return null;
        }

        $at = new \DateTimeImmutable((string) $row['created_at'], new \DateTimeZone('UTC'));
        $now = new \DateTimeImmutable('now', new \DateTimeZone('UTC'));

        return max(0, $now->getTimestamp() - $at->getTimestamp());
    }

    /**
     * @return array{event_type: string, id: int, created_at: string}|null
     */
    private function lastGrantedEventRowForUser(User $user, ?Club $club = null): ?array
    {
        return $this->lastGrantedEventRowForColumn('user_id', (int) $user->getId(), $club);
    }

    /**
     * @return array{event_type: string, id: int, created_at: string}|null
     */
    private function lastGrantedEventRowForStaff(StaffUser $staff, ?Club $club = null): ?array
    {
        return $this->lastGrantedEventRowForColumn('staff_user_id', (int) $staff->getId(), $club);
    }

    /**
     * @return array{event_type: string, id: int, created_at: string}|null
     */
    private function lastGrantedEventRowForColumn(string $column, int $id, ?Club $club = null): ?array
    {
        if (!\in_array($column, ['user_id', 'staff_user_id'], true) || $id <= 0) {
            return null;
        }

        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'pid' => $id,
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        $sql = "SELECT event_type, id, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM access_logs
                WHERE result = 'granted'
                  AND {$column} = :pid
                  AND created_at >= :from
                  AND created_at < :to" . $scope['sql'] . '
                ORDER BY id DESC
                LIMIT 1';

        $row = $this->connection()->executeQuery($sql, $params)->fetchAssociative();
        if ($row === false) {
            return null;
        }

        return [
            'event_type' => (string) ($row['event_type'] ?? ''),
            'id' => (int) ($row['id'] ?? 0),
            'created_at' => (string) ($row['created_at'] ?? ''),
        ];
    }

    /**
     * @return array{is_inside: bool, club_id: ?int, last_entry_at: ?string, last_exit_at: ?string}
     */
    public function getUserAccessSnapshot(User $user, ?Club $club = null): array
    {
        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'user_id' => $user->getId(),
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        $sql = 'SELECT
                    (SELECT event_type
                     FROM access_logs
                     WHERE result = \'granted\'
                       AND user_id = :user_id
                       AND created_at >= :from
                       AND created_at < :to' . $scope['sql'] . '
                     ORDER BY created_at DESC, id DESC
                     LIMIT 1) AS last_type,
                    (SELECT created_at
                     FROM access_logs
                     WHERE result = \'granted\'
                       AND user_id = :user_id
                       AND created_at >= :from
                       AND created_at < :to' . $scope['sql'] . '
                     ORDER BY created_at DESC, id DESC
                     LIMIT 1) AS last_at,
                    MAX(CASE WHEN event_type = \'entry\' THEN created_at END) AS last_entry_at,
                    MAX(CASE WHEN event_type = \'exit\' THEN created_at END) AS last_exit_at
                FROM access_logs
                WHERE result = \'granted\'
                  AND user_id = :user_id
                  AND created_at >= :from
                  AND created_at < :to' . $scope['sql'];

        $row = $this->connection()->executeQuery($sql, $params)->fetchAssociative() ?: [];
        $lastEntry = !empty($row['last_entry_at']) ? new \DateTimeImmutable((string) $row['last_entry_at']) : null;
        $lastExit = !empty($row['last_exit_at']) ? new \DateTimeImmutable((string) $row['last_exit_at']) : null;
        $isInside = ($row['last_type'] ?? '') === 'entry'
            && !empty($row['last_at'])
            && $this->entryStillFresh((string) $row['last_at']);

        return [
            'is_inside' => $isInside,
            'club_id' => $club?->getId(),
            'last_entry_at' => $lastEntry?->format(\DateTimeInterface::ATOM),
            'last_exit_at' => $lastExit?->format(\DateTimeInterface::ATOM),
        ];
    }

    private function connection(): Connection
    {
        return $this->em->getConnection();
    }
}
