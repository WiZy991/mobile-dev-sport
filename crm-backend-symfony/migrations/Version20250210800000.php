<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210800000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add feedbacks table';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS feedbacks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER DEFAULT NULL, type VARCHAR(20) NOT NULL, rating INTEGER NOT NULL, comment CLOB DEFAULT NULL, reference_id VARCHAR(50) DEFAULT NULL, created_at DATETIME NOT NULL, FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_feedbacks_user ON feedbacks (user_id)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS feedbacks (id INT AUTO_INCREMENT NOT NULL, user_id INT DEFAULT NULL, type VARCHAR(20) NOT NULL, rating INT NOT NULL, comment LONGTEXT DEFAULT NULL, reference_id VARCHAR(50) DEFAULT NULL, created_at DATETIME NOT NULL, INDEX IDX_feedbacks_user (user_id), PRIMARY KEY(id))');
            $this->addSql('ALTER TABLE feedbacks ADD CONSTRAINT FK_feedbacks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE feedbacks DROP FOREIGN KEY FK_feedbacks_user');
        }
        $this->addSql('DROP TABLE IF EXISTS feedbacks');
    }
}
