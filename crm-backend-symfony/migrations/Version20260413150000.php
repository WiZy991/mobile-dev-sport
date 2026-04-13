<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\PostgreSQLPlatform;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260413150000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add trainers.description for trainer bio shown in the mobile app';
    }

    public function up(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->addSql('ALTER TABLE trainers ADD COLUMN description CLOB DEFAULT NULL');

            return;
        }

        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE trainers ADD COLUMN description TEXT DEFAULT NULL');

            return;
        }

        $this->addSql('ALTER TABLE trainers ADD description LONGTEXT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->throwIrreversibleMigrationException('SQLite: drop column description is not supported safely.');
        }
        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE trainers DROP COLUMN description');

            return;
        }
        $this->addSql('ALTER TABLE trainers DROP description');
    }
}
