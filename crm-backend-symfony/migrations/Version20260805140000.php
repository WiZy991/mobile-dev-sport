<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Статус публикации тренера в клиентском приложении
 * (published / moderation / hidden) — фильтр API без обновления APK.
 */
final class Version20260805140000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'trainers.publication_status for app visibility (published/moderation/hidden)';
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('trainers')) {
            return;
        }
        if ($this->columnExists('trainers', 'publication_status')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        // DEFAULT published: существующие тренеры не исчезнут из приложения после деплоя.
        if ($sqlite) {
            $this->addSql("ALTER TABLE trainers ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'published'");
        } else {
            $this->addSql("ALTER TABLE trainers ADD publication_status VARCHAR(20) DEFAULT 'published' NOT NULL");
        }
        $this->addSql("UPDATE trainers SET publication_status = 'published' WHERE publication_status IS NULL OR publication_status = '' OR publication_status NOT IN ('published', 'moderation', 'hidden')");
    }

    public function down(Schema $schema): void
    {
        if (!$this->tableExists('trainers') || !$this->columnExists('trainers', 'publication_status')) {
            return;
        }
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($sqlite) {
            // SQLite: drop column не везде поддерживается — оставляем колонку.
            return;
        }
        $this->addSql('ALTER TABLE trainers DROP publication_status');
    }
}
