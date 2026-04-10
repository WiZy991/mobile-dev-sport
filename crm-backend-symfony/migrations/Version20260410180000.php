<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\PostgreSQLPlatform;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260410180000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create promotions table for mobile app banners';
    }

    public function up(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        $isSqlite = $platform instanceof SQLitePlatform;
        $isPg = $platform instanceof PostgreSQLPlatform;

        if ($isSqlite) {
            $this->addSql("CREATE TABLE promotions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title VARCHAR(120) NOT NULL, subtitle VARCHAR(255) DEFAULT NULL, button_text VARCHAR(50) NOT NULL, action_type VARCHAR(20) NOT NULL, action_value VARCHAR(255) DEFAULT NULL, bg_from VARCHAR(9) NOT NULL, bg_to VARCHAR(9) NOT NULL, sort_order INTEGER NOT NULL, is_active BOOLEAN NOT NULL, created_at DATETIME NOT NULL)");
            return;
        }

        if ($isPg) {
            $this->addSql("CREATE TABLE promotions (id SERIAL NOT NULL, title VARCHAR(120) NOT NULL, subtitle VARCHAR(255) DEFAULT NULL, button_text VARCHAR(50) NOT NULL, action_type VARCHAR(20) NOT NULL, action_value VARCHAR(255) DEFAULT NULL, bg_from VARCHAR(9) NOT NULL, bg_to VARCHAR(9) NOT NULL, sort_order INT NOT NULL, is_active BOOLEAN NOT NULL, created_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL, PRIMARY KEY(id))");
            return;
        }

        $this->addSql("CREATE TABLE promotions (id INT AUTO_INCREMENT NOT NULL, title VARCHAR(120) NOT NULL, subtitle VARCHAR(255) DEFAULT NULL, button_text VARCHAR(50) NOT NULL, action_type VARCHAR(20) NOT NULL, action_value VARCHAR(255) DEFAULT NULL, bg_from VARCHAR(9) NOT NULL, bg_to VARCHAR(9) NOT NULL, sort_order INT NOT NULL, is_active TINYINT(1) NOT NULL, created_at DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)', PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB");
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE promotions');
    }
}

