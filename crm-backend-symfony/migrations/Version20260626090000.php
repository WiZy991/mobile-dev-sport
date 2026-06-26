<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260626090000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Access alarms (tailgating / double-entry detected by camera)';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS access_alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, club_id INTEGER DEFAULT NULL, user_id INTEGER DEFAULT NULL, access_log_id INTEGER DEFAULT NULL, type VARCHAR(30) NOT NULL, device_id VARCHAR(100) DEFAULT NULL, people_count INTEGER NOT NULL, raw_data VARCHAR(255) DEFAULT NULL, details CLOB DEFAULT NULL, created_at DATETIME NOT NULL, FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL, FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL, FOREIGN KEY (access_log_id) REFERENCES access_logs (id) ON DELETE SET NULL)');
            $this->addSql('CREATE INDEX IF NOT EXISTS idx_access_alarms_created_at ON access_alarms (created_at)');
            $this->addSql('CREATE INDEX IF NOT EXISTS idx_access_alarms_club_created ON access_alarms (club_id, created_at)');
        } else {
            $this->addSql('CREATE TABLE access_alarms (id INT AUTO_INCREMENT NOT NULL, club_id INT DEFAULT NULL, user_id INT DEFAULT NULL, access_log_id INT DEFAULT NULL, type VARCHAR(30) NOT NULL, device_id VARCHAR(100) DEFAULT NULL, people_count INT NOT NULL, raw_data VARCHAR(255) DEFAULT NULL, details JSON DEFAULT NULL, created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', INDEX idx_access_alarms_created_at (created_at), INDEX idx_access_alarms_club_created (club_id, created_at), INDEX IDX_access_alarms_user (user_id), INDEX IDX_access_alarms_log (access_log_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE access_alarms ADD CONSTRAINT FK_access_alarms_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL');
            $this->addSql('ALTER TABLE access_alarms ADD CONSTRAINT FK_access_alarms_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL');
            $this->addSql('ALTER TABLE access_alarms ADD CONSTRAINT FK_access_alarms_log FOREIGN KEY (access_log_id) REFERENCES access_logs (id) ON DELETE SET NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS access_alarms');
    }
}
