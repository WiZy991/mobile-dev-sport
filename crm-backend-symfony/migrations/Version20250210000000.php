<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210000000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add is_blocked column to users table for app access control';
    }

    public function up(Schema $schema): void
    {
        if ($this->columnExists('users', 'is_blocked')) {
            return;
        }

        if ($this->connection->getDatabasePlatform() instanceof SQLitePlatform) {
            $this->addSql('ALTER TABLE users ADD COLUMN is_blocked INTEGER NOT NULL DEFAULT 0');
        } else {
            $this->addSql('ALTER TABLE users ADD is_blocked TINYINT(1) NOT NULL DEFAULT 0');
        }
    }

    public function down(Schema $schema): void
    {
        if (!$this->columnExists('users', 'is_blocked')) {
            return;
        }

        $this->addSql('ALTER TABLE users DROP is_blocked');
    }

    private function columnExists(string $table, string $column): bool
    {
        $conn = $this->connection;

        if ($conn->getDatabasePlatform() instanceof SQLitePlatform) {
            $n = (int) $conn->fetchOne(
                "SELECT COUNT(*) FROM pragma_table_info('{$table}') WHERE name = ?",
                [$column]
            );

            return $n > 0;
        }

        $db = $conn->fetchOne('SELECT DATABASE()');
        if ($db === null || $db === '') {
            return false;
        }

        $n = (int) $conn->fetchOne(
            'SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?',
            [$db, $table, $column]
        );

        return $n > 0;
    }
}
