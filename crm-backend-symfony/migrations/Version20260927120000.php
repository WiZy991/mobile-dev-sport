<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Бэкап sales.club_id для аренды тренеров (раньше не писался — в списке «—» и фильтр по залу не находил).
 */
final class Version20260927120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Backfill sales.club_id for trainer rentals from payments / product_name';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        if (!$this->tableExists('sales') || !$this->columnExists('sales', 'club_id')) {
            return;
        }

        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        // 1) Из платежа аренды (payments.club_id → sales.club_id).
        if ($this->tableExists('payments') && $this->columnExists('payments', 'club_id')) {
            try {
                if ($sqlite) {
                    $this->connection->executeStatement(
                        'UPDATE sales
                         SET club_id = (
                             SELECT pay.club_id FROM payments pay
                             WHERE pay.sale_id = sales.id AND pay.club_id IS NOT NULL
                             LIMIT 1
                         )
                         WHERE club_id IS NULL
                           AND EXISTS (
                               SELECT 1 FROM payments pay
                               WHERE pay.sale_id = sales.id AND pay.club_id IS NOT NULL
                           )'
                    );
                } else {
                    $this->connection->executeStatement(
                        'UPDATE sales s
                         INNER JOIN payments pay ON pay.sale_id = s.id AND pay.club_id IS NOT NULL
                         SET s.club_id = pay.club_id
                         WHERE s.club_id IS NULL'
                    );
                }
            } catch (\Throwable) {
            }
        }

        // 2) Из хвоста названия: «Аренда клуба (тренер) — 30 дн. — {club name}».
        if (!$this->tableExists('clubs')) {
            return;
        }

        try {
            $clubs = $this->connection->fetchAllAssociative(
                'SELECT id, name FROM clubs WHERE name IS NOT NULL AND TRIM(name) <> \'\''
            );
        } catch (\Throwable) {
            return;
        }

        foreach ($clubs as $club) {
            $id = (int) ($club['id'] ?? 0);
            $name = trim((string) ($club['name'] ?? ''));
            if ($id <= 0 || $name === '') {
                continue;
            }
            try {
                $this->connection->executeStatement(
                    'UPDATE sales
                     SET club_id = ?
                     WHERE club_id IS NULL
                       AND product_name LIKE ?
                       AND product_name LIKE ?',
                    [$id, 'Аренда клуба (тренер)%', '%— ' . $name]
                );
            } catch (\Throwable) {
            }
        }
    }

    public function down(Schema $schema): void
    {
        // no-op
    }
}
