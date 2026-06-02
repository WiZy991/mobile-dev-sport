<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260602173000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Add mobile api tokens for staff users';
    }

    public function up(Schema $schema): void
    {
        if (!$this->columnExists('staff_users', 'api_refresh_token')) {
            $this->addSql('ALTER TABLE staff_users ADD api_refresh_token VARCHAR(64) DEFAULT NULL');
        }
        if (!$this->columnExists('staff_users', 'api_access_token')) {
            $this->addSql('ALTER TABLE staff_users ADD api_access_token VARCHAR(64) DEFAULT NULL');
        }
        if (!$this->columnExists('staff_users', 'api_access_token_expires_at')) {
            $this->addSql('ALTER TABLE staff_users ADD api_access_token_expires_at DATETIME DEFAULT NULL');
        }
        if (!$this->indexExists('staff_users', 'UNIQ_staff_users_api_access_token')) {
            $this->addSql('CREATE UNIQUE INDEX UNIQ_staff_users_api_access_token ON staff_users (api_access_token)');
        }
        if (!$this->indexExists('staff_users', 'UNIQ_staff_users_api_refresh_token')) {
            $this->addSql('CREATE UNIQUE INDEX UNIQ_staff_users_api_refresh_token ON staff_users (api_refresh_token)');
        }
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_staff_users_api_access_token ON staff_users');
        $this->addSql('DROP INDEX UNIQ_staff_users_api_refresh_token ON staff_users');
        $this->addSql('ALTER TABLE staff_users DROP api_refresh_token, DROP api_access_token, DROP api_access_token_expires_at');
    }
}
