<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Индексы под быстрые mobile read: occupancy, notifications, subscriptions.
 */
final class Version20260730150000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add indexes for access_logs occupancy, notifications list, sales by subscription';
    }

    public function up(Schema $schema): void
    {
        $this->createIndexIfMissing(
            'access_logs',
            'idx_access_logs_result_created',
            'CREATE INDEX idx_access_logs_result_created ON access_logs (result, created_at)'
        );
        $this->createIndexIfMissing(
            'access_logs',
            'idx_access_logs_result_user_created',
            'CREATE INDEX idx_access_logs_result_user_created ON access_logs (result, user_id, created_at)'
        );
        $this->createIndexIfMissing(
            'notifications',
            'idx_notifications_user_created',
            'CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at)'
        );
        $this->createIndexIfMissing(
            'notifications',
            'idx_notifications_user_read',
            'CREATE INDEX idx_notifications_user_read ON notifications (user_id, read_at)'
        );
        $this->createIndexIfMissing(
            'sales',
            'idx_sales_subscription_id',
            'CREATE INDEX idx_sales_subscription_id ON sales (subscription_id)'
        );

        $this->addSql('SELECT 1');
    }

    public function down(Schema $schema): void
    {
        $this->dropIndexIfExists('access_logs', 'idx_access_logs_result_created');
        $this->dropIndexIfExists('access_logs', 'idx_access_logs_result_user_created');
        $this->dropIndexIfExists('notifications', 'idx_notifications_user_created');
        $this->dropIndexIfExists('notifications', 'idx_notifications_user_read');
        $this->dropIndexIfExists('sales', 'idx_sales_subscription_id');
    }

    private function createIndexIfMissing(string $table, string $index, string $sql): void
    {
        if ($this->indexExists($table, $index)) {
            return;
        }
        $this->addSql($sql);
    }

    private function dropIndexIfExists(string $table, string $index): void
    {
        if (!$this->indexExists($table, $index)) {
            return;
        }
        $this->addSql(sprintf('DROP INDEX %s ON %s', $index, $table));
    }

    private function indexExists(string $table, string $index): bool
    {
        return (bool) $this->connection->fetchOne(
            'SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? LIMIT 1',
            [$table, $index]
        );
    }
}
