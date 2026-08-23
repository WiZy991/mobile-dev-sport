<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/** Прайс услуг тренера (JSON) для карточки в клиентском приложении. */
final class Version20260821180000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'trainers.services_json for trainer price list (from N rub)';
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('trainers') || $this->columnExists('trainers', 'services_json')) {
            return;
        }
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($sqlite) {
            $this->addSql('ALTER TABLE trainers ADD COLUMN services_json CLOB DEFAULT NULL');
        } else {
            $this->addSql('ALTER TABLE trainers ADD services_json LONGTEXT DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        if (!$this->tableExists('trainers') || !$this->columnExists('trainers', 'services_json')) {
            return;
        }
        if ($this->connection->getDatabasePlatform() instanceof SQLitePlatform) {
            return;
        }
        $this->addSql('ALTER TABLE trainers DROP services_json');
    }
}
