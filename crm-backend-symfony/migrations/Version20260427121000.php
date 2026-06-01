<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Привязка access_logs к клубу + индексы под отчёты.
 */
final class Version20260427121000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Add club_id to access_logs and reporting indexes';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            if (!$this->columnExists('access_logs', 'club_id')) {
                $this->connection->executeStatement(
                    'ALTER TABLE access_logs ADD COLUMN club_id INTEGER DEFAULT NULL REFERENCES clubs(id) ON DELETE SET NULL',
                );
            }
            $this->connection->executeStatement(
                'CREATE INDEX IF NOT EXISTS idx_access_logs_created_at ON access_logs (created_at)',
            );
            $this->connection->executeStatement(
                'CREATE INDEX IF NOT EXISTS idx_access_logs_user_event_created ON access_logs (user_id, event_type, created_at)',
            );
            $this->connection->executeStatement(
                'CREATE INDEX IF NOT EXISTS idx_access_logs_club_created ON access_logs (club_id, created_at)',
            );

            return;
        }

        if (!$this->columnExists('access_logs', 'club_id')) {
            $this->connection->executeStatement('ALTER TABLE access_logs ADD club_id INT DEFAULT NULL');
        }
        if (!$this->foreignKeyExists('access_logs', 'FK_access_logs_club')) {
            $this->connection->executeStatement(
                'ALTER TABLE access_logs ADD CONSTRAINT FK_access_logs_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL',
            );
        }
        if (!$this->indexExists('access_logs', 'idx_access_logs_created_at')) {
            $this->connection->executeStatement(
                'CREATE INDEX idx_access_logs_created_at ON access_logs (created_at)',
            );
        }
        if (!$this->indexExists('access_logs', 'idx_access_logs_user_event_created')) {
            $this->connection->executeStatement(
                'CREATE INDEX idx_access_logs_user_event_created ON access_logs (user_id, event_type, created_at)',
            );
        }
        if (!$this->indexExists('access_logs', 'idx_access_logs_club_created')) {
            $this->connection->executeStatement(
                'CREATE INDEX idx_access_logs_club_created ON access_logs (club_id, created_at)',
            );
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql('DROP INDEX IF EXISTS idx_access_logs_club_created');
            $this->addSql('DROP INDEX IF EXISTS idx_access_logs_user_event_created');
            $this->addSql('DROP INDEX IF EXISTS idx_access_logs_created_at');

            return;
        }

        $this->addSql('ALTER TABLE access_logs DROP FOREIGN KEY FK_access_logs_club');
        $this->addSql('DROP INDEX idx_access_logs_club_created ON access_logs');
        $this->addSql('DROP INDEX idx_access_logs_user_event_created ON access_logs');
        $this->addSql('DROP INDEX idx_access_logs_created_at ON access_logs');
        $this->addSql('ALTER TABLE access_logs DROP COLUMN club_id');
    }
}
