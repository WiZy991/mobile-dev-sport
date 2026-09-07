<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * OTP по телефону, подтверждение email, поля приветственного экрана.
 */
final class Version20260903120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'phone_otp_challenges + users.email_verified_at';
    }

    public function isTransactional(): bool
    {
        return false;
    }

    public function up(Schema $schema): void
    {
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($this->tableExists('users')) {
            if (!$this->columnExists('users', 'email_verified_at')) {
                $this->connection->executeStatement(
                    $sqlite
                        ? 'ALTER TABLE users ADD COLUMN email_verified_at DATETIME DEFAULT NULL'
                        : 'ALTER TABLE users ADD COLUMN email_verified_at DATETIME DEFAULT NULL'
                );
            }
            if (!$this->columnExists('users', 'email_verify_token')) {
                $this->connection->executeStatement(
                    'ALTER TABLE users ADD COLUMN email_verify_token VARCHAR(64) DEFAULT NULL'
                );
            }
            if (!$this->columnExists('users', 'email_verify_token_expires_at')) {
                $this->connection->executeStatement(
                    'ALTER TABLE users ADD COLUMN email_verify_token_expires_at DATETIME DEFAULT NULL'
                );
            }
            if (!$sqlite && !$this->indexExists('users', 'uniq_users_email_verify_token')) {
                try {
                    $this->connection->executeStatement(
                        'CREATE UNIQUE INDEX uniq_users_email_verify_token ON users (email_verify_token)'
                    );
                } catch (\Throwable) {
                }
            }
        }

        if ($this->tableExists('phone_otp_challenges')) {
            return;
        }

        if ($sqlite) {
            $this->connection->executeStatement(
                'CREATE TABLE phone_otp_challenges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    phone VARCHAR(32) NOT NULL,
                    channel VARCHAR(20) NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    sent_at DATETIME DEFAULT NULL,
                    delivery_code VARCHAR(8) DEFAULT NULL,
                    deeplink_token VARCHAR(64) DEFAULT NULL,
                    registration_ticket VARCHAR(64) DEFAULT NULL,
                    registration_ticket_expires_at DATETIME DEFAULT NULL,
                    consumed_at DATETIME DEFAULT NULL,
                    created_at DATETIME NOT NULL
                )'
            );
            $this->connection->executeStatement(
                'CREATE INDEX idx_phone_otp_phone_created ON phone_otp_challenges (phone, created_at)'
            );
        } else {
            $this->connection->executeStatement(
                'CREATE TABLE phone_otp_challenges (
                    id INT AUTO_INCREMENT NOT NULL,
                    phone VARCHAR(32) NOT NULL,
                    channel VARCHAR(20) NOT NULL,
                    code_hash VARCHAR(255) NOT NULL,
                    expires_at DATETIME NOT NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    sent_at DATETIME DEFAULT NULL,
                    delivery_code VARCHAR(8) DEFAULT NULL,
                    deeplink_token VARCHAR(64) DEFAULT NULL,
                    registration_ticket VARCHAR(64) DEFAULT NULL,
                    registration_ticket_expires_at DATETIME DEFAULT NULL,
                    consumed_at DATETIME DEFAULT NULL,
                    created_at DATETIME NOT NULL,
                    INDEX idx_phone_otp_phone_created (phone, created_at),
                    UNIQUE INDEX uniq_phone_otp_deeplink (deeplink_token),
                    UNIQUE INDEX uniq_phone_otp_reg_ticket (registration_ticket),
                    PRIMARY KEY(id)
                ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB'
            );
        }
    }

    public function down(Schema $schema): void
    {
        if ($this->tableExists('phone_otp_challenges')) {
            $this->connection->executeStatement('DROP TABLE phone_otp_challenges');
        }
    }
}
