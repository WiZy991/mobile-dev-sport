<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * access_logs.staff_user_id — проходы тренеров из приложения специалиста.
 */
final class Version20260928120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'access_logs.staff_user_id + backfill from FITNESSCLUB:STAFF QR';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('access_logs')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->columnExists('access_logs', 'staff_user_id')) {
            if ($sqlite) {
                $this->connection->executeStatement(
                    'ALTER TABLE access_logs ADD COLUMN staff_user_id INTEGER DEFAULT NULL'
                );
            } else {
                $this->connection->executeStatement(
                    'ALTER TABLE access_logs ADD staff_user_id INT DEFAULT NULL'
                );
                try {
                    $this->connection->executeStatement(
                        'ALTER TABLE access_logs ADD CONSTRAINT FK_access_logs_staff_user FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE SET NULL'
                    );
                } catch (\Throwable) {
                }
                try {
                    $this->connection->executeStatement(
                        'CREATE INDEX IDX_access_logs_staff_user ON access_logs (staff_user_id)'
                    );
                } catch (\Throwable) {
                }
                try {
                    $this->connection->executeStatement(
                        'CREATE INDEX idx_access_logs_staff_event_created ON access_logs (staff_user_id, event_type, created_at)'
                    );
                } catch (\Throwable) {
                }
            }
        }

        // Бэкап: FITNESSCLUB:STAFF:{id}:…
        if (!$this->columnExists('access_logs', 'staff_user_id') || !$this->tableExists('staff_users')) {
            return;
        }

        try {
            if ($sqlite) {
                $this->connection->executeStatement(
                    "UPDATE access_logs
                     SET staff_user_id = CAST(
                         substr(raw_data, length('FITNESSCLUB:STAFF:') + 1,
                             instr(substr(raw_data, length('FITNESSCLUB:STAFF:') + 1), ':') - 1
                         ) AS INTEGER
                     )
                     WHERE staff_user_id IS NULL
                       AND raw_data LIKE 'FITNESSCLUB:STAFF:%:%'
                       AND EXISTS (
                           SELECT 1 FROM staff_users su
                           WHERE su.id = CAST(
                               substr(raw_data, length('FITNESSCLUB:STAFF:') + 1,
                                   instr(substr(raw_data, length('FITNESSCLUB:STAFF:') + 1), ':') - 1
                               ) AS INTEGER
                           )
                       )"
                );
            } else {
                $this->connection->executeStatement(
                    "UPDATE access_logs al
                     INNER JOIN staff_users su ON su.id = CAST(
                         SUBSTRING_INDEX(SUBSTRING_INDEX(al.raw_data, ':', 3), ':', -1) AS UNSIGNED
                     )
                     SET al.staff_user_id = su.id
                     WHERE al.staff_user_id IS NULL
                       AND al.raw_data LIKE 'FITNESSCLUB:STAFF:%'"
                );
            }
        } catch (\Throwable) {
        }
    }

    public function down(Schema $schema): void
    {
        // no-op
    }
}
