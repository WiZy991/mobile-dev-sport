<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260601140000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Store mobile API access token with expiry (Bearer auth for /api/v1/*)';
    }

    public function up(Schema $schema): void
    {
        if (!$this->columnExists('users', 'api_access_token')) {
            $this->addSql('ALTER TABLE users ADD api_access_token VARCHAR(64) DEFAULT NULL');
        }
        if (!$this->columnExists('users', 'api_access_token_expires_at')) {
            $this->addSql('ALTER TABLE users ADD api_access_token_expires_at DATETIME DEFAULT NULL');
        }
        if (!$this->indexExists('users', 'UNIQ_users_api_access_token')) {
            $this->addSql('CREATE UNIQUE INDEX UNIQ_users_api_access_token ON users (api_access_token)');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_users_api_access_token ON users');
        $this->addSql('ALTER TABLE users DROP api_access_token, DROP api_access_token_expires_at');
    }
}
