<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Email карточки тренера — связь с регистрацией в staffapp.
 */
final class Version20260827200000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'trainers.email for staffapp register auto-claim';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('trainers')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->columnExists('trainers', 'email')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE trainers ADD COLUMN email VARCHAR(180) DEFAULT NULL'
                    : 'ALTER TABLE trainers ADD COLUMN email VARCHAR(180) DEFAULT NULL'
            );
        }

        if (!$sqlite && !$this->indexExists('trainers', 'uniq_trainers_email')) {
            // Несколько NULL допустимы; дубликаты email нежелательны.
            try {
                $this->connection->executeStatement(
                    'CREATE UNIQUE INDEX uniq_trainers_email ON trainers (email)'
                );
            } catch (\Throwable) {
                // Если уже есть дубликаты — индекс не ставим, uniqueness проверим в коде.
            }
        }
    }

    public function down(Schema $schema): void
    {
        // Не удаляем колонку.
    }
}
