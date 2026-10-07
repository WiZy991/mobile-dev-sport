<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\AccessLog;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Визит = вход + следующий за ним выход того же человека.
 * Если следующим событием снова идёт вход (или выхода нет) — выход неизвестен.
 */
final class VisitSessionResolver
{
    /** Дальше выхода не ищем: дольше суток в зале — это уже не тот же визит. */
    private const MAX_SESSION_SECONDS = 24 * 3600;

    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * @param list<AccessLog> $entries входы (event_type = entry)
     *
     * @return array<int, array{exit_at: \DateTimeImmutable, auto: bool, duration_min: int}> по id входа
     */
    public function exitsForEntries(array $entries): array
    {
        $userIds = [];
        $staffIds = [];
        $minAt = null;
        $maxAt = null;
        foreach ($entries as $log) {
            if (!$log instanceof AccessLog || $log->getId() === null) {
                continue;
            }
            if ($log->getUser()?->getId() !== null) {
                $userIds[$log->getUser()->getId()] = true;
            } elseif ($log->getStaffUser()?->getId() !== null) {
                $staffIds[$log->getStaffUser()->getId()] = true;
            } else {
                continue;
            }
            $at = $log->getCreatedAt();
            $minAt = $minAt === null || $at < $minAt ? $at : $minAt;
            $maxAt = $maxAt === null || $at > $maxAt ? $at : $maxAt;
        }
        if ($minAt === null || $maxAt === null) {
            return [];
        }

        $conds = [];
        $params = [
            'from' => $minAt->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s'),
            'to' => $maxAt->setTimezone(new \DateTimeZone('UTC'))
                ->modify('+' . self::MAX_SESSION_SECONDS . ' seconds')->format('Y-m-d H:i:s'),
        ];
        $types = [];
        if ($userIds !== []) {
            $conds[] = 'user_id IN (:uids)';
            $params['uids'] = array_keys($userIds);
            $types['uids'] = \Doctrine\DBAL\ArrayParameterType::INTEGER;
        }
        if ($staffIds !== []) {
            $conds[] = 'staff_user_id IN (:sids)';
            $params['sids'] = array_keys($staffIds);
            $types['sids'] = \Doctrine\DBAL\ArrayParameterType::INTEGER;
        }

        try {
            $rows = $this->em->getConnection()->fetchAllAssociative(
                "SELECT id, user_id, staff_user_id, event_type, reason, created_at
                 FROM access_logs
                 WHERE result = 'granted'
                   AND created_at >= :from
                   AND created_at < :to
                   AND (" . implode(' OR ', $conds) . ')
                 ORDER BY id ASC',
                $params,
                $types,
            );
        } catch (\Throwable) {
            return [];
        }

        /** @var array<string, list<array<string, mixed>>> $byPerson */
        $byPerson = [];
        foreach ($rows as $r) {
            $key = $r['user_id'] !== null ? 'u' . $r['user_id'] : 's' . $r['staff_user_id'];
            $byPerson[$key][] = $r;
        }

        return $this->pair($byPerson);
    }

    /**
     * Визиты одного клиента (для ленты активности): пары вход → выход по хронологии.
     *
     * @param list<AccessLog> $logs granted-события клиента (любой порядок)
     *
     * @return list<array{entry: ?AccessLog, exit: ?AccessLog, date: \DateTimeImmutable, duration_min: ?int, auto: bool}>
     */
    public function sessionsFromLogs(array $logs): array
    {
        usort($logs, static fn (AccessLog $a, AccessLog $b): int => ($a->getId() ?? 0) <=> ($b->getId() ?? 0));

        $sessions = [];
        $open = null;
        foreach ($logs as $log) {
            if ($log->getEventType() === 'exit') {
                if ($open !== null && $this->withinSession($open->getCreatedAt(), $log->getCreatedAt())) {
                    $sessions[] = $this->session($open, $log);
                } else {
                    if ($open !== null) {
                        $sessions[] = $this->session($open, null);
                    }
                    $sessions[] = $this->session(null, $log);
                }
                $open = null;
                continue;
            }
            if ($open !== null) {
                $sessions[] = $this->session($open, null);
            }
            $open = $log;
        }
        if ($open !== null) {
            $sessions[] = $this->session($open, null);
        }

        return $sessions;
    }

    /**
     * @param array<string, list<array<string, mixed>>> $byPerson
     *
     * @return array<int, array{exit_at: \DateTimeImmutable, auto: bool, duration_min: int}>
     */
    private function pair(array $byPerson): array
    {
        $utc = new \DateTimeZone('UTC');
        $out = [];
        foreach ($byPerson as $events) {
            $count = \count($events);
            for ($i = 0; $i < $count; ++$i) {
                if (($events[$i]['event_type'] ?? '') !== 'entry') {
                    continue;
                }
                $next = $events[$i + 1] ?? null;
                if ($next === null || ($next['event_type'] ?? '') !== 'exit') {
                    continue;
                }
                $entryAt = new \DateTimeImmutable((string) $events[$i]['created_at'], $utc);
                $exitAt = new \DateTimeImmutable((string) $next['created_at'], $utc);
                if (!$this->withinSession($entryAt, $exitAt)) {
                    continue;
                }
                $out[(int) $events[$i]['id']] = [
                    'exit_at' => $exitAt,
                    'auto' => str_starts_with((string) ($next['reason'] ?? ''), 'auto_'),
                    'duration_min' => intdiv($exitAt->getTimestamp() - $entryAt->getTimestamp(), 60),
                ];
            }
        }

        return $out;
    }

    private function withinSession(\DateTimeImmutable $entryAt, \DateTimeImmutable $exitAt): bool
    {
        $delta = $exitAt->getTimestamp() - $entryAt->getTimestamp();

        return $delta >= 0 && $delta <= self::MAX_SESSION_SECONDS;
    }

    /**
     * @return array{entry: ?AccessLog, exit: ?AccessLog, date: \DateTimeImmutable, duration_min: ?int, auto: bool}
     */
    private function session(?AccessLog $entry, ?AccessLog $exit): array
    {
        $duration = null;
        if ($entry !== null && $exit !== null) {
            $duration = intdiv($exit->getCreatedAt()->getTimestamp() - $entry->getCreatedAt()->getTimestamp(), 60);
        }

        return [
            'entry' => $entry,
            'exit' => $exit,
            'date' => ($entry ?? $exit)->getCreatedAt(),
            'duration_min' => $duration,
            'auto' => $exit !== null && str_starts_with((string) $exit->getReason(), 'auto_'),
        ];
    }
}
