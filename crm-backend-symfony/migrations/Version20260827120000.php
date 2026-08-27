<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Проставить subscriptions.club_id из оплаченного payments.club_id.
 */
final class Version20260827120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Backfill subscriptions.club_id from paid payments';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('subscriptions') || !$this->tableExists('payments')) {
            return;
        }
        if (!$this->columnExists('subscriptions', 'club_id') || !$this->columnExists('payments', 'club_id')) {
            return;
        }

        // Последний paid-платёж с club_id → абонемент без клуба.
        $this->connection->executeStatement(
            'UPDATE subscriptions s
             INNER JOIN (
                 SELECT p.subscription_id AS sid, p.club_id AS cid
                 FROM payments p
                 INNER JOIN (
                     SELECT subscription_id, MAX(id) AS max_id
                     FROM payments
                     WHERE subscription_id IS NOT NULL
                       AND club_id IS NOT NULL
                       AND status = \'paid\'
                     GROUP BY subscription_id
                 ) latest ON latest.max_id = p.id
             ) src ON src.sid = s.id
             SET s.club_id = src.cid
             WHERE s.club_id IS NULL'
        );
    }

    public function down(Schema $schema): void
    {
        // Данные не откатываем.
    }
}
