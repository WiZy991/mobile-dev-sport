<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Привязка access_logs к клубу + индексы под отчёты:
 *   - сколько людей сейчас в зале (per club),
 *   - посещения за период,
 *   - последний вход клиента.
 */
final class Version20260427121000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add club_id to access_logs and reporting indexes';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql('ALTER TABLE access_logs ADD COLUMN club_id INTEGER DEFAULT NULL REFERENCES clubs(id) ON DELETE SET NULL');
            $this->addSql('CREATE INDEX IF NOT EXISTS idx_access_logs_created_at ON access_logs (created_at)');
            $this->addSql('CREATE INDEX IF NOT EXISTS idx_access_logs_user_event_created ON access_logs (user_id, event_type, created_at)');
            $this->addSql('CREATE INDEX IF NOT EXISTS idx_access_logs_club_created ON access_logs (club_id, created_at)');

            return;
        }

        $this->addSql('ALTER TABLE access_logs ADD club_id INT DEFAULT NULL');
        $this->addSql('ALTER TABLE access_logs ADD CONSTRAINT FK_access_logs_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL');
        $this->addSql('CREATE INDEX idx_access_logs_created_at ON access_logs (created_at)');
        $this->addSql('CREATE INDEX idx_access_logs_user_event_created ON access_logs (user_id, event_type, created_at)');
        $this->addSql('CREATE INDEX idx_access_logs_club_created ON access_logs (club_id, created_at)');
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
