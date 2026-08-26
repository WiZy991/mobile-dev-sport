<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Клубы для мобильного приложения: show_in_app + фото регистрации.
 */
final class Version20260826180000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'clubs.show_in_app + clubs.registration_image_path; seed open halls';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('clubs')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->columnExists('clubs', 'show_in_app')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE clubs ADD COLUMN show_in_app INTEGER NOT NULL DEFAULT 0'
                    : 'ALTER TABLE clubs ADD COLUMN show_in_app TINYINT(1) NOT NULL DEFAULT 0'
            );
        }

        if (!$this->columnExists('clubs', 'registration_image_path')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE clubs ADD COLUMN registration_image_path VARCHAR(255) DEFAULT NULL'
                    : 'ALTER TABLE clubs ADD COLUMN registration_image_path VARCHAR(255) DEFAULT NULL'
            );
        }

        // Известные открытые залы (Де-Фриз / Седанка) — если есть в БД.
        $this->connection->executeStatement(
            'UPDATE clubs SET show_in_app = 1 WHERE id IN (2, 11)'
        );
    }

    public function down(Schema $schema): void
    {
        // Не удаляем колонки — безопасно для prod rollback без потери данных формы.
    }
}
