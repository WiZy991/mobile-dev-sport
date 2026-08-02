<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Группы клиентов со скидкой % на абонементы.
 */
final class Version20260802120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Add client_groups and users.client_group_id for subscription discounts';
    }

    public function up(Schema $schema): void
    {
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if (!$this->tableExists('client_groups')) {
            if ($sqlite) {
                $this->addSql('CREATE TABLE client_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    discount_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
                    organization_id INTEGER NOT NULL,
                    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
                )');
                $this->addSql('CREATE INDEX IDX_client_groups_org ON client_groups (organization_id)');
            } else {
                $this->addSql('CREATE TABLE client_groups (
                    id INT AUTO_INCREMENT NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    discount_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
                    organization_id INT NOT NULL,
                    INDEX IDX_client_groups_org (organization_id),
                    PRIMARY KEY(id),
                    CONSTRAINT FK_client_groups_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
                ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
            }
        }

        if ($this->tableExists('users') && !$this->columnExists('users', 'client_group_id')) {
            if ($sqlite) {
                $this->addSql('ALTER TABLE users ADD COLUMN client_group_id INTEGER DEFAULT NULL REFERENCES client_groups (id) ON DELETE SET NULL');
                $this->addSql('CREATE INDEX IDX_users_client_group ON users (client_group_id)');
            } else {
                $this->addSql('ALTER TABLE users ADD client_group_id INT DEFAULT NULL');
                $this->addSql('ALTER TABLE users ADD CONSTRAINT FK_users_client_group FOREIGN KEY (client_group_id) REFERENCES client_groups (id) ON DELETE SET NULL');
                $this->addSql('CREATE INDEX IDX_users_client_group ON users (client_group_id)');
            }
        }
    }

    public function down(Schema $schema): void
    {
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($this->tableExists('users') && $this->columnExists('users', 'client_group_id')) {
            if (!$sqlite) {
                if ($this->foreignKeyExists('users', 'FK_users_client_group')) {
                    $this->addSql('ALTER TABLE users DROP FOREIGN KEY FK_users_client_group');
                }
                if ($this->indexExists('users', 'IDX_users_client_group')) {
                    $this->addSql('DROP INDEX IDX_users_client_group ON users');
                }
                $this->addSql('ALTER TABLE users DROP COLUMN client_group_id');
            }
        }

        if ($this->tableExists('client_groups')) {
            $this->addSql('DROP TABLE client_groups');
        }
    }
}
