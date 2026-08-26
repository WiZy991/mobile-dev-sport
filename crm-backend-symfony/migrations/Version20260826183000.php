<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Формат QR входа на клуб: ascii (Купера) | wiegand (Седанка).
 */
final class Version20260826183000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'clubs.entry_qr_format (ascii|wiegand); seed club 11 = wiegand';
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

        if (!$this->columnExists('clubs', 'entry_qr_format')) {
            $this->connection->executeStatement(
                $sqlite
                    ? "ALTER TABLE clubs ADD COLUMN entry_qr_format VARCHAR(16) NOT NULL DEFAULT 'ascii'"
                    : "ALTER TABLE clubs ADD COLUMN entry_qr_format VARCHAR(16) NOT NULL DEFAULT 'ascii'"
            );
        }

        $this->connection->executeStatement(
            "UPDATE clubs SET entry_qr_format = 'wiegand' WHERE id = 11"
        );
    }

    public function down(Schema $schema): void
    {
        // Не удаляем колонку — безопасный rollback без потери настроек.
    }
}
