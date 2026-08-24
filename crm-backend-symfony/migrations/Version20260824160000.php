<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Мультизал аренда тренера: staff_club_rentals, active_club, цены по клубам.
 *
 * DDL выполняется сразу через connection (не через отложенный addSql),
 * иначе seed/backfill падает: колонок ещё нет.
 */
final class Version20260824160000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'staff_club_rentals + staff_users.active_club_id + payments.club_id + clubs.trainer_rental_amount_rub';
    }

    public function isTransactional(): bool
    {
        // MySQL DDL делает implicit commit — без транзакции безопаснее для partial retry.
        return false;
    }

    public function up(Schema $schema): void
    {
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($this->tableExists('clubs') && !$this->columnExists('clubs', 'trainer_rental_amount_rub')) {
            $this->connection->executeStatement(
                $sqlite
                    ? 'ALTER TABLE clubs ADD COLUMN trainer_rental_amount_rub INTEGER DEFAULT NULL'
                    : 'ALTER TABLE clubs ADD trainer_rental_amount_rub INT DEFAULT NULL'
            );
        }

        if ($this->tableExists('staff_users') && !$this->columnExists('staff_users', 'active_club_id')) {
            if ($sqlite) {
                $this->connection->executeStatement(
                    'ALTER TABLE staff_users ADD COLUMN active_club_id INTEGER DEFAULT NULL'
                );
            } else {
                $this->connection->executeStatement(
                    'ALTER TABLE staff_users ADD active_club_id INT DEFAULT NULL'
                );
                if (!$this->foreignKeyExists('staff_users', 'FK_staff_users_active_club')) {
                    $this->connection->executeStatement(
                        'ALTER TABLE staff_users ADD CONSTRAINT FK_staff_users_active_club FOREIGN KEY (active_club_id) REFERENCES clubs (id) ON DELETE SET NULL'
                    );
                }
                if (!$this->indexExists('staff_users', 'IDX_staff_users_active_club')) {
                    $this->connection->executeStatement(
                        'CREATE INDEX IDX_staff_users_active_club ON staff_users (active_club_id)'
                    );
                }
            }
        }

        if ($this->tableExists('payments') && !$this->columnExists('payments', 'club_id')) {
            if ($sqlite) {
                $this->connection->executeStatement(
                    'ALTER TABLE payments ADD COLUMN club_id INTEGER DEFAULT NULL'
                );
            } else {
                $this->connection->executeStatement(
                    'ALTER TABLE payments ADD club_id INT DEFAULT NULL'
                );
                if (!$this->foreignKeyExists('payments', 'FK_payments_club')) {
                    $this->connection->executeStatement(
                        'ALTER TABLE payments ADD CONSTRAINT FK_payments_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL'
                    );
                }
                if (!$this->indexExists('payments', 'IDX_payments_club')) {
                    $this->connection->executeStatement(
                        'CREATE INDEX IDX_payments_club ON payments (club_id)'
                    );
                }
            }
        }

        if (!$this->tableExists('staff_club_rentals')) {
            if ($sqlite) {
                $this->connection->executeStatement(<<<'SQL'
CREATE TABLE staff_club_rentals (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    staff_user_id INTEGER NOT NULL,
    club_id INTEGER NOT NULL,
    paid_until DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE,
    FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
)
SQL);
                $this->connection->executeStatement(
                    'CREATE UNIQUE INDEX uniq_staff_club_rental ON staff_club_rentals (staff_user_id, club_id)'
                );
                $this->connection->executeStatement(
                    'CREATE INDEX idx_staff_club_rental_staff ON staff_club_rentals (staff_user_id)'
                );
                $this->connection->executeStatement(
                    'CREATE INDEX idx_staff_club_rental_club ON staff_club_rentals (club_id)'
                );
            } else {
                $this->connection->executeStatement(<<<'SQL'
CREATE TABLE staff_club_rentals (
    id INT AUTO_INCREMENT NOT NULL,
    staff_user_id INT NOT NULL,
    club_id INT NOT NULL,
    paid_until DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)',
    updated_at DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)',
    INDEX idx_staff_club_rental_staff (staff_user_id),
    INDEX idx_staff_club_rental_club (club_id),
    UNIQUE INDEX uniq_staff_club_rental (staff_user_id, club_id),
    PRIMARY KEY(id),
    CONSTRAINT FK_staff_club_rental_staff FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE CASCADE,
    CONSTRAINT FK_staff_club_rental_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB
SQL);
            }
        }

        // Только после реального появления колонок.
        $this->seedRentalClubsAndPrices();
        $this->backfillLegacyRentals();
    }

    public function down(Schema $schema): void
    {
        if ($this->connection->getDatabasePlatform() instanceof SQLitePlatform) {
            return;
        }
        if ($this->tableExists('staff_club_rentals')) {
            $this->addSql('DROP TABLE staff_club_rentals');
        }
        if ($this->tableExists('payments') && $this->columnExists('payments', 'club_id')) {
            if ($this->foreignKeyExists('payments', 'FK_payments_club')) {
                $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_payments_club');
            }
            if ($this->indexExists('payments', 'IDX_payments_club')) {
                $this->addSql('DROP INDEX IDX_payments_club ON payments');
            }
            $this->addSql('ALTER TABLE payments DROP club_id');
        }
        if ($this->tableExists('staff_users') && $this->columnExists('staff_users', 'active_club_id')) {
            if ($this->foreignKeyExists('staff_users', 'FK_staff_users_active_club')) {
                $this->addSql('ALTER TABLE staff_users DROP FOREIGN KEY FK_staff_users_active_club');
            }
            if ($this->indexExists('staff_users', 'IDX_staff_users_active_club')) {
                $this->addSql('DROP INDEX IDX_staff_users_active_club ON staff_users');
            }
            $this->addSql('ALTER TABLE staff_users DROP active_club_id');
        }
        if ($this->tableExists('clubs') && $this->columnExists('clubs', 'trainer_rental_amount_rub')) {
            $this->addSql('ALTER TABLE clubs DROP trainer_rental_amount_rub');
        }
    }

    private function seedRentalClubsAndPrices(): void
    {
        if (!$this->tableExists('clubs') || !$this->columnExists('clubs', 'trainer_rental_amount_rub')) {
            return;
        }

        $orgId = $this->connection->fetchOne('SELECT id FROM organizations ORDER BY id ASC LIMIT 1');
        $orgId = $orgId !== false && $orgId !== null ? (int) $orgId : null;
        $hasOrgCol = $this->columnExists('clubs', 'organization_id');

        $venues = [
            ['ТЦ Седанка Сити', 'ул. Полетаева, 6д', 30000, ['полетаева', 'седанка']],
            ['ТЦ Формат', 'ул. Центральная, 18, 2 этаж', 25000, ['центральная', 'формат']],
            ['ТЦ Новый де фриз', 'ул. Купера, 2, 2 этаж', 25000, ['купера', 'де фриз', 'де-фриз', 'дефриз']],
        ];

        foreach ($venues as [$name, $address, $amount, $needles]) {
            $clubId = $this->findClubIdByNeedles($needles);
            if ($clubId === null) {
                $row = [
                    'name' => $name,
                    'address' => $address,
                    'phone' => null,
                    'email' => null,
                    'working_hours' => null,
                    'amenities_json' => null,
                    'max_capacity' => null,
                    'latitude' => null,
                    'longitude' => null,
                    'trainer_rental_amount_rub' => $amount,
                ];
                if ($hasOrgCol && $orgId !== null) {
                    $row['organization_id'] = $orgId;
                }
                $this->connection->insert('clubs', $row);
            } else {
                $this->connection->executeStatement(
                    'UPDATE clubs SET trainer_rental_amount_rub = ? WHERE id = ?',
                    [$amount, $clubId],
                );
            }
        }
    }

    /** @param list<string> $needles */
    private function findClubIdByNeedles(array $needles): ?int
    {
        $rows = $this->connection->fetchAllAssociative('SELECT id, name, address FROM clubs');
        foreach ($rows as $row) {
            $hay = mb_strtolower((string) ($row['name'] ?? '') . ' ' . (string) ($row['address'] ?? ''));
            foreach ($needles as $needle) {
                if ($needle !== '' && str_contains($hay, mb_strtolower($needle))) {
                    return (int) $row['id'];
                }
            }
        }

        return null;
    }

    private function backfillLegacyRentals(): void
    {
        if (!$this->tableExists('staff_users') || !$this->tableExists('staff_club_rentals') || !$this->tableExists('clubs')) {
            return;
        }
        if (!$this->columnExists('staff_users', 'rental_paid_until')) {
            return;
        }
        if (!$this->columnExists('clubs', 'trainer_rental_amount_rub')) {
            return;
        }

        $defaultClubId = $this->connection->fetchOne(
            'SELECT id FROM clubs WHERE trainer_rental_amount_rub IS NOT NULL ORDER BY id ASC LIMIT 1'
        );
        if ($defaultClubId === false || $defaultClubId === null) {
            $defaultClubId = $this->connection->fetchOne('SELECT id FROM clubs ORDER BY id ASC LIMIT 1');
        }
        if ($defaultClubId === false || $defaultClubId === null) {
            return;
        }
        $defaultClubId = (int) $defaultClubId;

        $hasActiveClub = $this->columnExists('staff_users', 'active_club_id');
        $select = $hasActiveClub
            ? 'SELECT id, rental_paid_until, active_club_id FROM staff_users WHERE rental_paid_until IS NOT NULL'
            : 'SELECT id, rental_paid_until FROM staff_users WHERE rental_paid_until IS NOT NULL';

        $staffRows = $this->connection->fetchAllAssociative($select);
        foreach ($staffRows as $row) {
            $staffId = (int) $row['id'];
            $until = (string) $row['rental_paid_until'];
            $exists = (int) $this->connection->fetchOne(
                'SELECT COUNT(*) FROM staff_club_rentals WHERE staff_user_id = ?',
                [$staffId],
            );
            if ($exists > 0) {
                continue;
            }
            $now = (new \DateTimeImmutable('now'))->format('Y-m-d H:i:s');
            $this->connection->insert('staff_club_rentals', [
                'staff_user_id' => $staffId,
                'club_id' => $defaultClubId,
                'paid_until' => $until,
                'updated_at' => $now,
            ]);
            if ($hasActiveClub && empty($row['active_club_id'])) {
                $this->connection->executeStatement(
                    'UPDATE staff_users SET active_club_id = ? WHERE id = ?',
                    [$defaultClubId, $staffId],
                );
            }
        }
    }
}
