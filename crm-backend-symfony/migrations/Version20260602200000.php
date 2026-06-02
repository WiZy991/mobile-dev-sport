<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260602200000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Staff in-app notifications for support tickets';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS staff_notifications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, staff_user_id INTEGER NOT NULL, type VARCHAR(50) NOT NULL, title VARCHAR(150) NOT NULL, body CLOB NOT NULL, reference_id VARCHAR(100) DEFAULT NULL, created_at DATETIME NOT NULL, read_at DATETIME DEFAULT NULL, FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_staff_notifications_user ON staff_notifications (staff_user_id)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_staff_notifications_read ON staff_notifications (read_at)');
        } else {
            $this->addSql('CREATE TABLE staff_notifications (id INT AUTO_INCREMENT NOT NULL, staff_user_id INT NOT NULL, type VARCHAR(50) NOT NULL, title VARCHAR(150) NOT NULL, body LONGTEXT NOT NULL, reference_id VARCHAR(100) DEFAULT NULL, created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', read_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\', INDEX IDX_staff_notifications_user (staff_user_id), INDEX IDX_staff_notifications_read (read_at), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE staff_notifications ADD CONSTRAINT FK_staff_notifications_user FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE staff_notifications DROP FOREIGN KEY FK_staff_notifications_user');
        }
        $this->addSql('DROP TABLE IF EXISTS staff_notifications');
    }
}
