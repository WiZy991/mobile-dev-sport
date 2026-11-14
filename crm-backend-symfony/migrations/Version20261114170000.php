<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Дополнительные кнопки акций со ссылкой.
 */
final class Version20261114170000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'promotions.buttons_json — свои кнопки со ссылкой';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('promotions') || $this->columnExists('promotions', 'buttons_json')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        $this->connection->executeStatement(
            $sqlite
                ? 'ALTER TABLE promotions ADD COLUMN buttons_json CLOB DEFAULT NULL'
                : 'ALTER TABLE promotions ADD COLUMN buttons_json LONGTEXT DEFAULT NULL'
        );
    }

    public function down(Schema $schema): void
    {
    }
}
