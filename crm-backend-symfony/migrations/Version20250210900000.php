<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210900000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add guest_passes table';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS guest_passes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, owner_id INTEGER NOT NULL, guest_name VARCHAR(100) DEFAULT NULL, qr_token VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT \'active\', created_at DATETIME NOT NULL, used_at DATETIME DEFAULT NULL, FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_guest_passes_owner ON guest_passes (owner_id)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS guest_passes (id INT AUTO_INCREMENT NOT NULL, owner_id INT NOT NULL, guest_name VARCHAR(100) DEFAULT NULL, qr_token VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT \'active\', created_at DATETIME NOT NULL, used_at DATETIME DEFAULT NULL, INDEX IDX_guest_passes_owner (owner_id), PRIMARY KEY(id))');
            $this->addSql('ALTER TABLE guest_passes ADD CONSTRAINT FK_guest_passes_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE guest_passes DROP FOREIGN KEY FK_guest_passes_owner');
        }
        $this->addSql('DROP TABLE IF EXISTS guest_passes');
    }
}
