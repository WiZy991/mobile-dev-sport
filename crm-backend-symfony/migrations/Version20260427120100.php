<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Очередь команд от CRM к шлюзам клубов (открытие двери из админки и т.п.).
 * Шлюз клуба long-poll'ит /api/v1/gateway/commands.
 */
final class Version20260427120100 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Create gateway_commands table (queue from CRM to club gateways)';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql(<<<'SQL'
                CREATE TABLE gateway_commands (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    club_id INTEGER NOT NULL,
                    kind VARCHAR(40) NOT NULL,
                    payload_json TEXT DEFAULT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at DATETIME NOT NULL,
                    delivered_at DATETIME DEFAULT NULL,
                    done_at DATETIME DEFAULT NULL,
                    expires_at DATETIME DEFAULT NULL,
                    result_json TEXT DEFAULT NULL,
                    issued_by VARCHAR(150) DEFAULT NULL,
                    CONSTRAINT FK_gateway_commands_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
                )
            SQL);
            $this->addSql("CREATE INDEX IDX_gateway_commands_club_status ON gateway_commands (club_id, status)");
            $this->addSql("CREATE INDEX IDX_gateway_commands_created ON gateway_commands (created_at)");
        } else {
            $this->addSql(<<<'SQL'
                CREATE TABLE gateway_commands (
                    id INT AUTO_INCREMENT NOT NULL,
                    club_id INT NOT NULL,
                    kind VARCHAR(40) NOT NULL,
                    payload_json LONGTEXT DEFAULT NULL,
                    status VARCHAR(20) NOT NULL,
                    created_at DATETIME NOT NULL,
                    delivered_at DATETIME DEFAULT NULL,
                    done_at DATETIME DEFAULT NULL,
                    expires_at DATETIME DEFAULT NULL,
                    result_json LONGTEXT DEFAULT NULL,
                    issued_by VARCHAR(150) DEFAULT NULL,
                    INDEX IDX_gateway_commands_club_status (club_id, status),
                    INDEX IDX_gateway_commands_created (created_at),
                    PRIMARY KEY (id)
                ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB
            SQL);
            $this->addSql("ALTER TABLE gateway_commands ADD CONSTRAINT FK_gateway_commands_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE");
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql("DROP TABLE gateway_commands");
    }
}
