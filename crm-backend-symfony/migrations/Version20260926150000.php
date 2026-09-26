<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * sales.club_id + шире payment_method; бэкап клуба из абонемента/клиента.
 */
final class Version20260926150000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'sales.club_id + payment_method length; backfill club from subscription/user';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('sales')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->columnExists('sales', 'club_id')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE sales ADD COLUMN club_id INTEGER DEFAULT NULL'
                    : 'ALTER TABLE sales ADD COLUMN club_id INT DEFAULT NULL'
            );
            if (!$sqlite) {
                try {
                    $this->connection->executeStatement(
                        'ALTER TABLE sales ADD CONSTRAINT FK_sales_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL'
                    );
                } catch (\Throwable) {
                }
                try {
                    $this->connection->executeStatement('CREATE INDEX IDX_sales_club ON sales (club_id)');
                } catch (\Throwable) {
                }
            }
        }

        if (!$sqlite && $this->columnExists('sales', 'payment_method')) {
            try {
                $this->connection->executeStatement(
                    'ALTER TABLE sales MODIFY payment_method VARCHAR(32) NOT NULL'
                );
            } catch (\Throwable) {
            }
        }

        // Бэкап клуба: сначала абонемент, потом клуб клиента.
        if ($this->columnExists('sales', 'club_id') && $this->tableExists('subscriptions')) {
            try {
                $this->connection->executeStatement(
                    'UPDATE sales s
                     INNER JOIN subscriptions sub ON s.subscription_id = sub.id
                     SET s.club_id = sub.club_id
                     WHERE s.club_id IS NULL AND sub.club_id IS NOT NULL'
                );
            } catch (\Throwable) {
            }
        }
        if ($this->columnExists('sales', 'club_id') && $this->tableExists('users') && $this->columnExists('users', 'club_id')) {
            try {
                $this->connection->executeStatement(
                    'UPDATE sales s
                     INNER JOIN users u ON s.user_id = u.id
                     SET s.club_id = u.club_id
                     WHERE s.club_id IS NULL AND u.club_id IS NOT NULL'
                );
            } catch (\Throwable) {
            }
        }
    }

    public function down(Schema $schema): void
    {
        // no-op: не откатываем club_id в проде
    }
}
