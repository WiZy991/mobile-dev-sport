<?php

declare(strict_types=1);

namespace App\Service\Reports;

use App\Entity\AccessLog;
use App\Entity\StaffUser;
use App\Service\Integration\WiegandEntryQrCodec;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Привязка проходов тренера к staff_users (журнал / occupancy).
 * Новые проходы пишут staff_user_id сразу; старые — из QR или Wiegand.
 */
final class AccessLogStaffLinker
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * Для списка журнала: проставить staffUser в памяти (+ опционально сохранить в БД).
     *
     * @param list<AccessLog> $logs
     *
     * @return int сколько логов получили staff
     */
    public function attachMissingStaff(array $logs, bool $persist = true): int
    {
        $fixed = 0;
        foreach ($logs as $log) {
            if (!$log instanceof AccessLog) {
                continue;
            }
            if ($log->getUser() !== null || $log->getStaffUser() !== null) {
                continue;
            }
            $staff = $this->resolveStaffFromRawData($log->getRawData());
            if (!$staff instanceof StaffUser) {
                continue;
            }
            $log->setStaffUser($staff);
            ++$fixed;
        }
        if ($persist && $fixed > 0) {
            $this->em->flush();
        }

        return $fixed;
    }

    public function resolveStaffFromRawData(string $raw): ?StaffUser
    {
        $raw = trim($raw);
        if ($raw === '') {
            return null;
        }

        $parts = explode(':', $raw);
        if (\count($parts) >= 3 && $parts[0] === 'FITNESSCLUB' && $parts[1] === 'STAFF') {
            $staffId = (int) $parts[2];
            if ($staffId <= 0) {
                return null;
            }

            $staff = $this->em->find(StaffUser::class, $staffId);

            return $staff instanceof StaffUser ? $staff : null;
        }

        if (!WiegandEntryQrCodec::isPayload($raw)) {
            return null;
        }
        // Не WiegandEntryQrCodec::parse(): там проверка «свежести» слота — старые логи отвалятся.
        $normalized = WiegandEntryQrCodec::normalize($raw);
        if ($normalized === null) {
            return null;
        }
        $uidMod = \strlen($normalized) === WiegandEntryQrCodec::LEGACY_LENGTH
            ? (int) substr($normalized, 0, 5) % WiegandEntryQrCodec::USER_MOD
            : (int) substr($normalized, 0, 4);
        if ($uidMod < 0 || $uidMod >= WiegandEntryQrCodec::USER_MOD) {
            return null;
        }

        /** @var list<StaffUser> $matches */
        $matches = $this->em->createQueryBuilder()
            ->select('s')
            ->from(StaffUser::class, 's')
            ->where('MOD(s.id, :modBase) = :uidMod')
            ->setParameter('modBase', WiegandEntryQrCodec::USER_MOD)
            ->setParameter('uidMod', $uidMod)
            ->getQuery()
            ->getResult();

        // Как на турникете: однозначный staff id % 10000.
        return \count($matches) === 1 ? $matches[0] : null;
    }

    /**
     * Массовый бэкап в БД (STAFF-строки + однозначный Wiegand).
     *
     * @return array{staff_qr: int, wiegand: int}
     */
    public function backfillDatabase(): array
    {
        $conn = $this->em->getConnection();
        $staffQr = 0;
        $wiegand = 0;

        try {
            $staffQr = $conn->executeStatement(
                "UPDATE access_logs al
                 INNER JOIN staff_users su ON su.id = CAST(
                     SUBSTRING_INDEX(SUBSTRING_INDEX(al.raw_data, ':', 3), ':', -1) AS UNSIGNED
                 )
                 SET al.staff_user_id = su.id
                 WHERE al.staff_user_id IS NULL
                   AND al.user_id IS NULL
                   AND al.raw_data LIKE 'FITNESSCLUB:STAFF:%'"
            );
        } catch (\Throwable) {
            $staffQr = 0;
        }

        // Wiegand: только где ровно один staff с id % 10000 = UUUU.
        try {
            $rows = $conn->fetchAllAssociative(
                "SELECT al.id AS log_id,
                        CAST(LEFT(LPAD(TRIM(al.raw_data), 7, '0'), 4) AS UNSIGNED) AS uid_mod
                 FROM access_logs al
                 WHERE al.staff_user_id IS NULL
                   AND al.user_id IS NULL
                   AND al.result = 'granted'
                   AND al.raw_data REGEXP '^[0-9]{4,9}$'"
            );
            $modCounts = [];
            $staffByMod = [];
            /** @var list<array{id: int}> $staffRows */
            $staffRows = $conn->fetchAllAssociative('SELECT id FROM staff_users');
            foreach ($staffRows as $sr) {
                $sid = (int) $sr['id'];
                $mod = $sid % WiegandEntryQrCodec::USER_MOD;
                $modCounts[$mod] = ($modCounts[$mod] ?? 0) + 1;
                $staffByMod[$mod] = $sid;
            }
            foreach ($rows as $row) {
                $mod = (int) ($row['uid_mod'] ?? -1);
                if ($mod < 0 || ($modCounts[$mod] ?? 0) !== 1) {
                    continue;
                }
                $staffId = $staffByMod[$mod] ?? null;
                if ($staffId === null) {
                    continue;
                }
                $n = $conn->executeStatement(
                    'UPDATE access_logs SET staff_user_id = ? WHERE id = ? AND staff_user_id IS NULL AND user_id IS NULL',
                    [$staffId, (int) $row['log_id']],
                );
                $wiegand += $n;
            }
        } catch (\Throwable) {
            $wiegand = 0;
        }

        return ['staff_qr' => (int) $staffQr, 'wiegand' => (int) $wiegand];
    }
}
