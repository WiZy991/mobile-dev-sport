<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\PostgreSQLPlatform;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260410183000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add image_path to promotions';
    }

    public function up(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->addSql('ALTER TABLE promotions ADD COLUMN image_path VARCHAR(255) DEFAULT NULL');
            return;
        }

        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE promotions ADD COLUMN image_path VARCHAR(255) DEFAULT NULL');
            return;
        }

        $this->addSql('ALTER TABLE promotions ADD image_path VARCHAR(255) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->throwIrreversibleMigrationException('SQLite: drop column image_path is not supported safely.');
        }
        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE promotions DROP COLUMN image_path');
            return;
        }
        $this->addSql('ALTER TABLE promotions DROP image_path');
    }
}

