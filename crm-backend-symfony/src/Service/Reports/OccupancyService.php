<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\AccessLog;
use App\Entity\Club;
use App\Entity\User;
use Doctrine\DBAL\Connection;
use Doctrine\ORM\EntityManagerInterface;

/**
 * «Сколько людей сейчас в зале» и кто именно.
 *
 * Логика: за окно присутствия для каждого клиента берём ПОСЛЕДНЕЕ granted-событие.
 * Если оно entry — клиент в зале; если exit — вышел.
 *
 * Важно: события с club_id = NULL (legacy / старые логи) считаются тем же залом,
 * что и текущий шлюз. Иначе вход без клуба + выход с club_id оставляют человека «в зале».
 *
 * Клубный день — Asia/Vladivostok (APP_TIMEZONE).
 */
final class OccupancyService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * Окно «сегодня» для витрины CRM (клубные сутки).
     *
     * @return array{from: string, to: string}
     */
    private function todayWindow(): array
    {
        $tzName = date_default_timezone_get() ?: 'Asia/Vladivostok';
        try {
            $clubTz = new \DateTimeZone($tzName);
        } catch (\Throwable) {
            $clubTz = new \DateTimeZone('Asia/Vladivostok');
        }

        $localStart = new \DateTimeImmutable('today', $clubTz);
        $localEnd = $localStart->modify('+1 day');

        return [
            'from' => $localStart->format('Y-m-d H:i:s'),
            'to' => $localEnd->format('Y-m-d H:i:s'),
        ];
    }

    /**
     * Окно для вход↔выход: с полуночи, но не короче 16 ч назад (защита после смены TZ).
     *
     * @return array{from: string, to: string}
     */
    private function presenceWindow(): array
    {
        $today = $this->todayWindow();
        $lookback = (new \DateTimeImmutable('-16 hours'))->format('Y-m-d H:i:s');

        return [
            'from' => min($today['from'], $lookback),
            'to' => $today['to'],
        ];
    }

    /**
     * Фильтр клуба: конкретный клуб ИЛИ legacy NULL (тот же человек / тот же зал).
     *
     * @return array{sql: string, params: array<string, mixed>}
     */
    private function clubScopeSql(?Club $club, string $column = 'club_id'): array
    {
        if (!$club instanceof Club) {
            return ['sql' => '', 'params' => []];
        }

        return [
            'sql' => " AND ({$column} IS NULL OR {$column} = :club_id)",
            'params' => ['club_id' => $club->getId()],
        ];
    }

    /** Сколько клиентов сейчас в зале (опционально по клубу; NULL-club считается этим залом). */
    public function countCurrentlyInside(?Club $club = null): int
    {
        $sql = $this->buildCurrentlyInsideSql($club, selectColumns: 'COUNT(*) AS cnt');
        $row = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAssociative();

        return (int) ($row['cnt'] ?? 0);
    }

    /**
     * @return list<array{user: User, entered_at: \DateTimeImmutable, club_id: ?int}>
     */
    public function listCurrentlyInside(?Club $club = null, int $limit = 200): array
    {
        $sql = $this->buildCurrentlyInsideSql(
            $club,
            selectColumns: 't.user_id AS user_id, t.last_at AS entered_at, t.last_club_id AS club_id',
            orderBy: 'entered_at DESC',
            limit: $limit,
        );

        $rows = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAllAssociative();
        if ($rows === []) {
            return [];
        }

        $userIds = array_values(array_unique(array_map(static fn (array $r): int => (int) $r['user_id'], $rows)));
        /** @var User[] $users */
        $users = $this->em->getRepository(User::class)->findBy(['id' => $userIds]);
        $byId = [];
        foreach ($users as $u) {
            $byId[$u->getId()] = $u;
        }

        $result = [];
        foreach ($rows as $r) {
            $user = $byId[(int) $r['user_id']] ?? null;
            if (!$user) {
                continue;
            }
            $clubRaw = $r['club_id'] ?? null;
            $result[] = [
                'user' => $user,
                'entered_at' => new \DateTimeImmutable((string) $r['entered_at']),
                'club_id' => $clubRaw !== null && $clubRaw !== '' ? (int) $clubRaw : null,
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
                ->setUser($row['user'])
                ->setClub($rowClub instanceof Club ? $rowClub : null)
                ->setRawData('ADMIN:FORCE_EXIT:' . $row['user']->getId())
                ->setDeviceId('crm-admin')
                ->setEventType('exit')
                ->setResult('granted')
                ->setReason('admin_clear_hall');
            $this->em->persist($log);
            ++$count;
        }
        if ($count > 0) {
            $this->em->flush();
        }

        return $count;
    }

    /**
     * Последнее событие по user_id (не по паре user+club) — иначе NULL-club «залипает».
     *
     * @return array{sql: string, params: array<string, mixed>}
     */
    private function buildCurrentlyInsideSql(
        ?Club $club,
        string $selectColumns,
        ?string $orderBy = null,
        ?int $limit = null,
    ): array {
        // То же окно, что у isUserCurrentlyInside — иначе CRM и турникет расходятся.
        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        // GROUP_CONCAT: последнее event_type / created_at / club_id по user_id.
        $inner = "SELECT user_id,
                         SUBSTRING_INDEX(GROUP_CONCAT(event_type ORDER BY created_at DESC, id DESC SEPARATOR ','), ',', 1) AS last_type,
                         SUBSTRING_INDEX(GROUP_CONCAT(DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') ORDER BY created_at DESC, id DESC SEPARATOR ','), ',', 1) AS last_at,
                         SUBSTRING_INDEX(GROUP_CONCAT(IFNULL(club_id, '') ORDER BY created_at DESC, id DESC SEPARATOR ','), ',', 1) AS last_club_id
                  FROM access_logs
                  WHERE result = 'granted'
                    AND user_id IS NOT NULL
                    AND created_at >= :from
                    AND created_at < :to" . $scope['sql'] . '
                  GROUP BY user_id
                  HAVING last_type = \'entry\'';

        $sql = "SELECT $selectColumns FROM ($inner) t";

        if ($orderBy !== null) {
            $sql .= "\n                ORDER BY $orderBy";
        }
        if ($limit !== null && $limit > 0) {
            $sql .= "\n                LIMIT $limit";
        }

        return ['sql' => $sql, 'params' => $params];
    }

    /** Сейчас ли клиент в зале — то же правило, что и счётчик (с учётом NULL club_id). */
    public function isUserCurrentlyInside(User $user, ?Club $club = null): bool
    {
        $window = $this->presenceWindow();
        $scope = $this->clubScopeSql($club);
        $params = [
            'user_id' => $user->getId(),
            'from' => $window['from'],
            'to' => $window['to'],
        ] + $scope['params'];

        $sql = 'SELECT event_type
                FROM access_logs
                WHERE result = \'granted\'
                  AND user_id = :user_id
                  AND created_at >= :from
                  AND created_at < :to' . $scope['sql'] . '
                ORDER BY created_at DESC, id DESC
                LIMIT 1';

        $row = $this->connection()->executeQuery($sql, $params)->fetchAssociative();
        if ($row === false) {
            return false;
        }

        return ($row['event_type'] ?? '') === 'entry';
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

        $fetchLast = function (string $eventType) use ($params, $scope): ?\DateTimeImmutable {
            $sql = 'SELECT created_at
                    FROM access_logs
                    WHERE result = \'granted\'
                      AND user_id = :user_id
                      AND event_type = :event_type
                      AND created_at >= :from
                      AND created_at < :to' . $scope['sql'] . '
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1';
            $queryParams = $params + ['event_type' => $eventType];
            $row = $this->connection()->executeQuery($sql, $queryParams)->fetchAssociative();
            if ($row === false) {
                return null;
            }

            return new \DateTimeImmutable((string) $row['created_at']);
        };

        $lastEntry = $fetchLast('entry');
        $lastExit = $fetchLast('exit');

        return [
            'is_inside' => $this->isUserCurrentlyInside($user, $club),
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
