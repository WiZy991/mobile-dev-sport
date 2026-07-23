<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\PostgreSQLPlatform;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260723160000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add trainers.phone for client-facing contact number';
    }

    public function up(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->addSql('ALTER TABLE trainers ADD COLUMN phone VARCHAR(50) DEFAULT NULL');

            return;
        }

        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE trainers ADD COLUMN phone VARCHAR(50) DEFAULT NULL');

            return;
        }

        $this->addSql('ALTER TABLE trainers ADD phone VARCHAR(50) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->throwIrreversibleMigrationException('SQLite: drop column phone is not supported safely.');
        }
        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE trainers DROP COLUMN phone');

            return;
        }
        $this->addSql('ALTER TABLE trainers DROP phone');
    }
}
