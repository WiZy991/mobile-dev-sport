<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\Club;
use App\Entity\User;
use Doctrine\DBAL\Connection;
use Doctrine\ORM\EntityManagerInterface;

/**
 * «Сколько людей сейчас в зале» и кто именно.
 *
 * Логика: за текущий день (с 00:00) для каждого клиента берём ПОСЛЕДНЕЕ событие
 * с result='granted'. Если оно eventType='entry' — клиент сейчас в зале.
 * Если eventType='exit' — он уже вышел.
 *
 * Корректно работает в обоих сценариях:
 *  - читаем только entry (нет считывателя на выход) — каждый зашедший считается «в зале»;
 *  - читаем entry/exit (двусторонний турникет) — пара entry→exit убирает клиента из зала.
 *
 * Внутренний день — Europe/Moscow (или иной локальный TZ сервера); реализация считает
 * границу дня по PHP-таймзоне сервера, чтобы совпасть с остальной CRM.
 */
final class OccupancyService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** Сколько клиентов сейчас в зале (опционально по конкретному клубу). */
    public function countCurrentlyInside(?Club $club = null): int
    {
        $sql = $this->buildCurrentlyInsideSql($club, selectColumns: 'COUNT(DISTINCT a.user_id) AS cnt');
        $row = $this->connection()->executeQuery($sql['sql'], $sql['params'])->fetchAssociative();

        return (int) ($row['cnt'] ?? 0);
    }

    /**
     * Полный список клиентов «в зале» с временем последнего входа.
     *
     * @return list<array{user: User, entered_at: \DateTimeImmutable, club_id: ?int}>
     */
    public function listCurrentlyInside(?Club $club = null, int $limit = 200): array
    {
        $sql = $this->buildCurrentlyInsideSql(
            $club,
            selectColumns: 'a.user_id AS user_id, MAX(a.created_at) AS entered_at, a.club_id AS club_id',
            groupBy: 'a.user_id, a.club_id',
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
            $result[] = [
                'user' => $user,
                'entered_at' => new \DateTimeImmutable((string) $r['entered_at']),
                'club_id' => $r['club_id'] !== null ? (int) $r['club_id'] : null,
            ];
        }

        return $result;
    }

    /**
     * Сборщик SQL: «последнее за сегодня granted-событие пользователя — это entry».
     * Группируем по (user_id, club_id), чтобы при мультиклубной франшизе клиент учитывался
     * именно в том клубе, где он сейчас находится.
     *
     * @return array{sql: string, params: array<string, mixed>}
     */
    private function buildCurrentlyInsideSql(
        ?Club $club,
        string $selectColumns,
        ?string $groupBy = null,
        ?string $orderBy = null,
        ?int $limit = null,
    ): array {
        $todayStart = (new \DateTimeImmutable('today'))->format('Y-m-d H:i:s');
        $tomorrow = (new \DateTimeImmutable('today'))->modify('+1 day')->format('Y-m-d H:i:s');

        $clubFilter = '';
        $params = [
            'from' => $todayStart,
            'to' => $tomorrow,
        ];
        if ($club instanceof Club) {
            $clubFilter = ' AND a.club_id = :club_id';
            $params['club_id'] = $club->getId();
        }

        // Подзапрос: последний timestamp granted-события за сегодня для каждого пользователя.
        // Затем берём только тех, у кого last event_type = 'entry'.
        $sql = "SELECT $selectColumns
                FROM access_logs a
                INNER JOIN (
                    SELECT user_id, MAX(created_at) AS max_at
                    FROM access_logs
                    WHERE result = 'granted'
                      AND user_id IS NOT NULL
                      AND created_at >= :from
                      AND created_at < :to
                    GROUP BY user_id
                ) last_events
                  ON last_events.user_id = a.user_id
                 AND last_events.max_at = a.created_at
                WHERE a.result = 'granted'
                  AND a.user_id IS NOT NULL
                  AND a.event_type = 'entry'
                  AND a.created_at >= :from
                  AND a.created_at < :to" . $clubFilter;

        if ($groupBy !== null) {
            $sql .= "\n                GROUP BY $groupBy";
        }
        if ($orderBy !== null) {
            $sql .= "\n                ORDER BY $orderBy";
        }
        if ($limit !== null && $limit > 0) {
            $sql .= "\n                LIMIT $limit";
        }

        return ['sql' => $sql, 'params' => $params];
    }

    /** Сейчас ли указанный клиент в зале (по тому же правилу). */
    public function isUserCurrentlyInside(User $user, ?Club $club = null): bool
    {
        $todayStart = (new \DateTimeImmutable('today'))->format('Y-m-d H:i:s');
        $tomorrow = (new \DateTimeImmutable('today'))->modify('+1 day')->format('Y-m-d H:i:s');

        $params = [
            'user_id' => $user->getId(),
            'from' => $todayStart,
            'to' => $tomorrow,
        ];
        $clubFilter = '';
        if ($club instanceof Club) {
            $clubFilter = ' AND club_id = :club_id';
            $params['club_id'] = $club->getId();
        }

        $sql = "SELECT event_type
                FROM access_logs
                WHERE result = 'granted'
                  AND user_id = :user_id
                  AND created_at >= :from
                  AND created_at < :to" . $clubFilter . "
                ORDER BY created_at DESC
                LIMIT 1";

        $row = $this->connection()->executeQuery($sql, $params)->fetchAssociative();
        if ($row === false) {
            return false;
        }

        return ($row['event_type'] ?? '') === 'entry';
    }

    private function connection(): Connection
    {
        return $this->em->getConnection();
    }
}
