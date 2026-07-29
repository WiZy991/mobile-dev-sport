<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Индексы для ускорения списка клиентов / поиска / фильтра по абонементам.
 */
final class Version20260730120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add indexes on users and subscriptions for clients list performance';
    }

    public function up(Schema $schema): void
    {
        $this->createIndexIfMissing('users', 'idx_users_name', 'CREATE INDEX idx_users_name ON users (name)');
        $this->createIndexIfMissing('users', 'idx_users_email', 'CREATE INDEX idx_users_email ON users (email)');
        $this->createIndexIfMissing('users', 'idx_users_phone', 'CREATE INDEX idx_users_phone ON users (phone)');
        $this->createIndexIfMissing('users', 'idx_users_club_id', 'CREATE INDEX idx_users_club_id ON users (club_id)');
        $this->createIndexIfMissing('subscriptions', 'idx_subscriptions_user_id', 'CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id)');
        $this->createIndexIfMissing('subscriptions', 'idx_subscriptions_user_status', 'CREATE INDEX idx_subscriptions_user_status ON subscriptions (user_id, status)');
        $this->createIndexIfMissing('subscriptions', 'idx_subscriptions_plan_id', 'CREATE INDEX idx_subscriptions_plan_id ON subscriptions (plan_id)');

        // Если все индексы уже были — миграция всё равно должна содержать SQL.
        $this->addSql('SELECT 1');
    }

    public function down(Schema $schema): void
    {
        $this->dropIndexIfExists('users', 'idx_users_name');
        $this->dropIndexIfExists('users', 'idx_users_email');
        $this->dropIndexIfExists('users', 'idx_users_phone');
        $this->dropIndexIfExists('users', 'idx_users_club_id');
        $this->dropIndexIfExists('subscriptions', 'idx_subscriptions_user_id');
        $this->dropIndexIfExists('subscriptions', 'idx_subscriptions_user_status');
        $this->dropIndexIfExists('subscriptions', 'idx_subscriptions_plan_id');
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
