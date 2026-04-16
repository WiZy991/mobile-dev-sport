<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Идемпотентно создаёт support_tickets, если таблицы нет (например, миграция Version20260413180000
 * отмечена выполненной, а таблица в MySQL отсутствует).
 */
final class Version20260413210000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Ensure support_tickets table exists (MySQL / SQLite)';
    }

    public function up(Schema $schema): void
    {
        $sm = $this->connection->createSchemaManager();
        if ($sm->tablesExist(['support_tickets'])) {
            return;
        }

        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE support_tickets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, user_id INTEGER DEFAULT NULL, subject VARCHAR(200) NOT NULL, message CLOB NOT NULL, category VARCHAR(32) NOT NULL, contact_email VARCHAR(180) DEFAULT NULL, status VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL, FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL)');
            $this->addSql('CREATE INDEX IDX_support_tickets_user ON support_tickets (user_id)');
            $this->addSql('CREATE INDEX IDX_support_tickets_status ON support_tickets (status)');
            $this->addSql('CREATE INDEX IDX_support_tickets_created ON support_tickets (created_at)');
        } else {
            $this->addSql('CREATE TABLE support_tickets (id INT AUTO_INCREMENT NOT NULL, user_id INT DEFAULT NULL, subject VARCHAR(200) NOT NULL, message LONGTEXT NOT NULL, category VARCHAR(32) NOT NULL, contact_email VARCHAR(180) DEFAULT NULL, status VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', INDEX IDX_support_tickets_user (user_id), INDEX IDX_support_tickets_status (status), INDEX IDX_support_tickets_created (created_at), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE support_tickets ADD CONSTRAINT FK_support_tickets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS support_tickets');
    }
}
