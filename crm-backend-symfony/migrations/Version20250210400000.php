<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210400000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add lead_notes and expenses for financial reports';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS lead_notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lead_id INTEGER NOT NULL, text CLOB NOT NULL, created_at DATETIME NOT NULL, FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_lead_notes_lead ON lead_notes (lead_id)');
            $this->addSql('CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, description VARCHAR(150) NOT NULL, amount DOUBLE PRECISION NOT NULL, category VARCHAR(50) DEFAULT NULL, date DATE NOT NULL, created_at DATETIME NOT NULL)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS lead_notes (id INT AUTO_INCREMENT NOT NULL, lead_id INT NOT NULL, text LONGTEXT NOT NULL, created_at DATETIME NOT NULL, INDEX IDX_lead_notes_lead (lead_id), PRIMARY KEY(id))');
            $this->addSql('ALTER TABLE lead_notes ADD CONSTRAINT FK_lead_notes_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE');
            $this->addSql('CREATE TABLE IF NOT EXISTS expenses (id INT AUTO_INCREMENT NOT NULL, description VARCHAR(150) NOT NULL, amount DOUBLE PRECISION NOT NULL, category VARCHAR(50) DEFAULT NULL, date DATE NOT NULL, created_at DATETIME NOT NULL, PRIMARY KEY(id))');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE lead_notes DROP FOREIGN KEY FK_lead_notes_lead');
        }
        $this->addSql('DROP TABLE IF EXISTS lead_notes');
        $this->addSql('DROP TABLE IF EXISTS expenses');
    }
}
