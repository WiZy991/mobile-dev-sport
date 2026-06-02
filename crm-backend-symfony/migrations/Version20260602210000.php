<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260602210000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Staff push tokens for mobile notifications';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS staff_push_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, staff_user_id INTEGER NOT NULL, token VARCHAR(255) NOT NULL, platform VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE)');
            $this->addSql('CREATE UNIQUE INDEX IF NOT EXISTS UNIQ_staff_push_token ON staff_push_tokens (token)');
        } else {
            $this->addSql('CREATE TABLE staff_push_tokens (id INT AUTO_INCREMENT NOT NULL, staff_user_id INT NOT NULL, token VARCHAR(255) NOT NULL, platform VARCHAR(20) NOT NULL, created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', updated_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', UNIQUE INDEX UNIQ_staff_push_token (token), INDEX IDX_staff_push_user (staff_user_id), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            $this->addSql('ALTER TABLE staff_push_tokens ADD CONSTRAINT FK_staff_push_user FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS staff_push_tokens');
    }
}
