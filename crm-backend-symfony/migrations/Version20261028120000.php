<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20261028120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Токены восстановления пароля (приложение и CRM)';
    }

    public function up(Schema $schema): void
    {
        if ($this->tableExists('password_reset_tokens')) {
            return;
        }

        $this->addSql(<<<'SQL'
CREATE TABLE password_reset_tokens (
    id INT AUTO_INCREMENT NOT NULL,
    audience VARCHAR(16) NOT NULL,
    user_id INT NOT NULL,
    email VARCHAR(180) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)',
    used_at DATETIME DEFAULT NULL COMMENT '(DC2Type:datetime_immutable)',
    created_at DATETIME NOT NULL COMMENT '(DC2Type:datetime_immutable)',
    ip VARCHAR(45) DEFAULT NULL,
    INDEX idx_pwd_reset_email (email),
    INDEX idx_pwd_reset_expires (expires_at),
    UNIQUE INDEX uniq_pwd_reset_token_hash (token_hash),
    PRIMARY KEY(id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB
SQL);
    }

    public function down(Schema $schema): void
    {
        if ($this->tableExists('password_reset_tokens')) {
            $this->addSql('DROP TABLE password_reset_tokens');
        }
    }
}
