<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Индекс под occupancy window-function (result + created_at + user_id + id).
 */
final class Version20260730180000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add covering index for access_logs occupancy window query';
    }

    public function up(Schema $schema): void
    {
        if ($this->indexExists('access_logs', 'idx_access_logs_occ_window')) {
            $this->addSql('SELECT 1');

            return;
        }
        $this->addSql(
            'CREATE INDEX idx_access_logs_occ_window ON access_logs (result, created_at, user_id, id)'
        );
    }

    public function down(Schema $schema): void
    {
        if (!$this->indexExists('access_logs', 'idx_access_logs_occ_window')) {
            return;
        }
        $this->addSql('DROP INDEX idx_access_logs_occ_window ON access_logs');
    }

    private function indexExists(string $table, string $index): bool
    {
        return (bool) $this->connection->fetchOne(
            'SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1',
            [$table, $index]
        );
    }
}
