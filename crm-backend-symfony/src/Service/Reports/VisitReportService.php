<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\AccessLog;
use App\Entity\Club;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Статистика посещений (успешные входы по QR) за период с разбивкой по клубам.
 */
final class VisitReportService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * @return array{
     *     rows: list<array{club_id: ?int, club_name: string, count: int}>,
     *     total: int
     * }
     */
    public function countByClub(\DateTimeImmutable $from, \DateTimeImmutable $toExclusive): array
    {
        /** @var list<array{clubId: ?int, cnt: int|string}> $aggregated */
        $aggregated = $this->em->createQueryBuilder()
            ->select('IDENTITY(a.club) AS clubId, COUNT(a.id) AS cnt')
            ->from(AccessLog::class, 'a')
            ->where('a.result = :result')
            ->andWhere('a.eventType = :eventType')
            ->andWhere('a.createdAt >= :from')
            ->andWhere('a.createdAt < :to')
            ->setParameter('result', 'granted')
            ->setParameter('eventType', 'entry')
            ->setParameter('from', $from)
            ->setParameter('to', $toExclusive)
            ->groupBy('a.club')
            ->getQuery()
            ->getResult();

        $countsByClubId = [];
        foreach ($aggregated as $row) {
            $key = $row['clubId'] !== null ? (int) $row['clubId'] : null;
            $countsByClubId[$key] = (int) $row['cnt'];
        }

        /** @var Club[] $clubs */
        $clubs = $this->em->getRepository(Club::class)->findBy([], ['name' => 'ASC']);

        $rows = [];
        foreach ($clubs as $club) {
            $id = $club->getId();
            $rows[] = [
                'club_id' => $id,
                'club_name' => $club->getName(),
                'count' => $countsByClubId[$id] ?? 0,
            ];
        }

        $withoutClub = $countsByClubId[null] ?? 0;
        if ($withoutClub > 0 || $clubs === []) {
            $rows[] = [
                'club_id' => null,
                'club_name' => 'Без клуба',
                'count' => $withoutClub,
            ];
        }

        $total = array_sum(array_map(static fn (array $r): int => $r['count'], $rows));

        usort($rows, static function (array $a, array $b): int {
            if ($a['count'] !== $b['count']) {
                return $b['count'] <=> $a['count'];
            }

            return strcasecmp($a['club_name'], $b['club_name']);
        });

        return [
            'rows' => $rows,
            'total' => $total,
        ];
    }
}
