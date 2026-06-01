<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;

/**
 * Проверки «уже есть в БД» — для prod, где schema:update мог опередить migrations.
 */
trait MigrationHelpers
{
    protected function tableExists(string $table): bool
    {
        return $this->connection->createSchemaManager()->tablesExist([$table]);
    }

    protected function columnExists(string $table, string $column): bool
    {
        if (!$this->tableExists($table)) {
            return false;
        }

        $columns = $this->connection->createSchemaManager()->listTableColumns($table);

        return isset($columns[$column]) || isset($columns[strtolower($column)]);
    }

    protected function indexExists(string $table, string $indexName): bool
    {
        if (!$this->tableExists($table)) {
            return false;
        }

        $indexes = $this->connection->createSchemaManager()->listTableIndexes($table);
        $lower = strtolower($indexName);

        foreach ($indexes as $name => $_) {
            if (strtolower((string) $name) === $lower) {
                return true;
            }
        }

        return false;
    }

    protected function foreignKeyExists(string $table, string $constraintName): bool
    {
        if ($this->connection->getDatabasePlatform() instanceof SQLitePlatform) {
            return false;
        }

        $count = $this->connection->fetchOne(
            'SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = ?',
            [$table, $constraintName, 'FOREIGN KEY'],
        );

        return (int) $count > 0;
    }
}
