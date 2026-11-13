<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * ЮKassa для приложения «Академия борьбы»: шлюз платежа и id платежа ЮKassa.
 */
final class Version20261113160000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'payments.gateway + yookassa_payment_id, длиннее payment_url';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('payments')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->columnExists('payments', 'gateway')) {
            $this->connection->executeStatement(
                $sqlite
                    ? "ALTER TABLE payments ADD COLUMN gateway VARCHAR(20) NOT NULL DEFAULT 'alfa'"
                    : "ALTER TABLE payments ADD gateway VARCHAR(20) NOT NULL DEFAULT 'alfa'"
            );
        }

        if (!$this->columnExists('payments', 'yookassa_payment_id')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE payments ADD COLUMN yookassa_payment_id VARCHAR(64) DEFAULT NULL'
                    : 'ALTER TABLE payments ADD yookassa_payment_id VARCHAR(64) DEFAULT NULL'
            );
        }

        if (!$this->indexExists('payments', 'uniq_payment_yookassa_id')) {
            $this->connection->executeStatement(
                'CREATE UNIQUE INDEX uniq_payment_yookassa_id ON payments (yookassa_payment_id)'
            );
        }

        if (!$sqlite && $this->columnExists('payments', 'payment_url')) {
            $this->connection->executeStatement(
                'ALTER TABLE payments MODIFY payment_url VARCHAR(2048) DEFAULT NULL'
            );
        }
    }

    public function down(Schema $schema): void
    {
        if (!$this->tableExists('payments')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($sqlite) {
            return;
        }

        if ($this->indexExists('payments', 'uniq_payment_yookassa_id')) {
            $this->connection->executeStatement('DROP INDEX uniq_payment_yookassa_id ON payments');
        }
        if ($this->columnExists('payments', 'yookassa_payment_id')) {
            $this->connection->executeStatement('ALTER TABLE payments DROP yookassa_payment_id');
        }
        if ($this->columnExists('payments', 'gateway')) {
            $this->connection->executeStatement('ALTER TABLE payments DROP gateway');
        }
    }
}
