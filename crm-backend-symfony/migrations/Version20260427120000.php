<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Per-club настройки шлюза турникета и СКУД PERCo (франшиза):
 *  - gateway_token: токен ПК-шлюза в клубе для авторизации в /api/v1/gateway/*
 *  - perco_*: подключение к локальному PERCo-Web в LAN клуба (используется ШЛЮЗОМ, не CRM)
 *  - gateway_last_seen_at: heartbeat
 */
final class Version20260427120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Clubs: gateway_token + per-club PERCo settings + gateway_last_seen_at';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql("ALTER TABLE clubs ADD COLUMN gateway_token VARCHAR(64) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN gateway_last_seen_at DATETIME DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN perco_base_url VARCHAR(255) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN perco_login VARCHAR(100) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN perco_password VARCHAR(255) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN perco_entry_device_id INT DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD COLUMN perco_verify_ssl SMALLINT DEFAULT 1 NOT NULL");
            $this->addSql("CREATE UNIQUE INDEX UNIQ_clubs_gateway_token ON clubs (gateway_token)");
        } else {
            $this->addSql("ALTER TABLE clubs ADD gateway_token VARCHAR(64) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD gateway_last_seen_at DATETIME DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD perco_base_url VARCHAR(255) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD perco_login VARCHAR(100) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD perco_password VARCHAR(255) DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD perco_entry_device_id INT DEFAULT NULL");
            $this->addSql("ALTER TABLE clubs ADD perco_verify_ssl TINYINT(1) DEFAULT 1 NOT NULL");
            $this->addSql("CREATE UNIQUE INDEX UNIQ_clubs_gateway_token ON clubs (gateway_token)");
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql("DROP INDEX UNIQ_clubs_gateway_token");
            foreach (['gateway_token', 'gateway_last_seen_at', 'perco_base_url', 'perco_login', 'perco_password', 'perco_entry_device_id', 'perco_verify_ssl'] as $c) {
                $this->addSql("ALTER TABLE clubs DROP COLUMN $c");
            }
        } else {
            $this->addSql("DROP INDEX UNIQ_clubs_gateway_token ON clubs");
            $this->addSql("ALTER TABLE clubs DROP gateway_token, DROP gateway_last_seen_at, DROP perco_base_url, DROP perco_login, DROP perco_password, DROP perco_entry_device_id, DROP perco_verify_ssl");
        }
    }
}
