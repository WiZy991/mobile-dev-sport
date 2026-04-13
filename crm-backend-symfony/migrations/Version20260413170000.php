<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260413170000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Store API refresh token on user for auth/refresh and logout invalidation';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD api_refresh_token VARCHAR(64) DEFAULT NULL');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_users_api_refresh_token ON users (api_refresh_token)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_users_api_refresh_token ON users');
        $this->addSql('ALTER TABLE users DROP api_refresh_token');
    }
}
