<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\PostgreSQLPlatform;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250330120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Staff CRM accounts (staff_users) + passport verification fields on users';
    }

    public function up(Schema $schema): void
    {
        $platform = $this->connection->getDatabasePlatform();
        $isSqlite = $platform instanceof SQLitePlatform;
        $isPg = $platform instanceof PostgreSQLPlatform;

        if ($isSqlite) {
            $this->addSql('CREATE TABLE staff_users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, email VARCHAR(180) NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL, roles CLOB NOT NULL, is_active BOOLEAN NOT NULL, created_at DATETIME NOT NULL, CONSTRAINT UNIQ_STAFF_EMAIL UNIQUE (email))');
        } elseif ($isPg) {
            $this->addSql('CREATE TABLE staff_users (id SERIAL NOT NULL, email VARCHAR(180) NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL, roles JSON NOT NULL, is_active BOOLEAN NOT NULL, created_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL, PRIMARY KEY(id))');
            $this->addSql('CREATE UNIQUE INDEX UNIQ_STAFF_EMAIL ON staff_users (email)');
        } else {
            $this->addSql('CREATE TABLE staff_users (id INT AUTO_INCREMENT NOT NULL, email VARCHAR(180) NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(100) NOT NULL, roles JSON NOT NULL, is_active TINYINT(1) NOT NULL, created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\', UNIQUE INDEX UNIQ_STAFF_EMAIL (email), PRIMARY KEY(id)) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        }

        if ($isSqlite) {
            $this->addSql("ALTER TABLE users ADD COLUMN passport_verification_status VARCHAR(20) NOT NULL DEFAULT 'none'");
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verified_at DATETIME DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_provider VARCHAR(50) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_subject VARCHAR(128) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_audit_json CLOB DEFAULT NULL');
        } elseif ($isPg) {
            $this->addSql("ALTER TABLE users ADD COLUMN passport_verification_status VARCHAR(20) NOT NULL DEFAULT 'none'");
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verified_at TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_provider VARCHAR(50) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_subject VARCHAR(128) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD COLUMN passport_verification_audit_json TEXT DEFAULT NULL');
        } else {
            $this->addSql("ALTER TABLE users ADD passport_verification_status VARCHAR(20) NOT NULL DEFAULT 'none'");
            $this->addSql('ALTER TABLE users ADD passport_verified_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
            $this->addSql('ALTER TABLE users ADD passport_verification_provider VARCHAR(50) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD passport_verification_subject VARCHAR(128) DEFAULT NULL');
            $this->addSql('ALTER TABLE users ADD passport_verification_audit_json LONGTEXT DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE staff_users');
        $platform = $this->connection->getDatabasePlatform();
        if ($platform instanceof SQLitePlatform) {
            $this->throwIrreversibleMigrationException('SQLite: cannot drop user verification columns safely.');
        }
        if ($platform instanceof PostgreSQLPlatform) {
            $this->addSql('ALTER TABLE users DROP COLUMN passport_verification_status');
            $this->addSql('ALTER TABLE users DROP COLUMN passport_verified_at');
            $this->addSql('ALTER TABLE users DROP COLUMN passport_verification_provider');
            $this->addSql('ALTER TABLE users DROP COLUMN passport_verification_subject');
            $this->addSql('ALTER TABLE users DROP COLUMN passport_verification_audit_json');
        } else {
            $this->addSql('ALTER TABLE users DROP passport_verification_status, DROP passport_verified_at, DROP passport_verification_provider, DROP passport_verification_subject, DROP passport_verification_audit_json');
        }
    }
}
