<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260518120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Сбер ID: sber_id и is_verified для клиентов приложения.';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD sber_id VARCHAR(128) DEFAULT NULL');
        $this->addSql('ALTER TABLE users ADD is_verified TINYINT NOT NULL DEFAULT 0');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_users_sber_id ON users (sber_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_users_sber_id ON users');
        $this->addSql('ALTER TABLE users DROP sber_id, DROP is_verified');
    }
}
