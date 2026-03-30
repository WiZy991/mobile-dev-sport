<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210700000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add notifications table';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER NOT NULL, type VARCHAR(50) NOT NULL, title VARCHAR(150) NOT NULL, body CLOB NOT NULL, created_at DATETIME NOT NULL, read_at DATETIME DEFAULT NULL, reference_id VARCHAR(100) DEFAULT NULL, FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_notifications_user ON notifications (user_id)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS notifications (id INT AUTO_INCREMENT NOT NULL, user_id INT NOT NULL, type VARCHAR(50) NOT NULL, title VARCHAR(150) NOT NULL, body LONGTEXT NOT NULL, created_at DATETIME NOT NULL, read_at DATETIME DEFAULT NULL, reference_id VARCHAR(100) DEFAULT NULL, INDEX IDX_notifications_user (user_id), PRIMARY KEY(id))');
            $this->addSql('ALTER TABLE notifications ADD CONSTRAINT FK_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE notifications DROP FOREIGN KEY FK_notifications_user');
        }
        $this->addSql('DROP TABLE IF EXISTS notifications');
    }
}
