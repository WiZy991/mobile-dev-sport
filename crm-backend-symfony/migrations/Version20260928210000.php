<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Повторный бэкап staff_user_id: STAFF-строки + однозначный Wiegand (цифровой QR).
 * Первая миграция не покрывала Wiegand — в журнале оставались «—».
 */
final class Version20260928210000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Backfill access_logs.staff_user_id for Wiegand trainer entries';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('access_logs')
            || !$this->columnExists('access_logs', 'staff_user_id')
            || !$this->tableExists('staff_users')
        ) {
            return;
        }

        // 1) FITNESSCLUB:STAFF:{id}:…
        try {
            $this->connection->executeStatement(
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
        }

        // 2) Wiegand UUUUTTC — только если ровно один staff с id % 10000 = UUUU.
        try {
            $this->connection->executeStatement(
                "UPDATE access_logs al
                 INNER JOIN (
                     SELECT MOD(id, 10000) AS uid_mod, MIN(id) AS staff_id
                     FROM staff_users
                     GROUP BY MOD(id, 10000)
                     HAVING COUNT(*) = 1
                 ) uniq ON uniq.uid_mod = CAST(LEFT(LPAD(TRIM(al.raw_data), 7, '0'), 4) AS UNSIGNED)
                 SET al.staff_user_id = uniq.staff_id
                 WHERE al.staff_user_id IS NULL
                   AND al.user_id IS NULL
                   AND al.result = 'granted'
                   AND al.raw_data REGEXP '^[0-9]{4,9}$'"
            );
        } catch (\Throwable) {
        }
    }

    public function down(Schema $schema): void
    {
        // no-op
    }
}
