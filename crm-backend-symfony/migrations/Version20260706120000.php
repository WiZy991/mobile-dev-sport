<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260706120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Multi-tenant: organizations table and organization_id on tenant entities';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE TABLE organizations (
            id INT AUTO_INCREMENT NOT NULL,
            name VARCHAR(150) NOT NULL,
            slug VARCHAR(80) NOT NULL,
            email VARCHAR(180) DEFAULT NULL,
            phone VARCHAR(32) DEFAULT NULL,
            inn VARCHAR(20) DEFAULT NULL,
            tariff VARCHAR(50) NOT NULL,
            is_active TINYINT(1) NOT NULL,
            demo_until DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            UNIQUE INDEX uniq_organization_slug (slug),
            PRIMARY KEY(id)
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');

        $this->addSql("INSERT INTO organizations (name, slug, tariff, is_active, created_at)
            VALUES ('WorldCashFit Demo', 'demo', 'demo', 1, NOW())");

        $tablesRequired = [
            'clubs', 'users', 'tags', 'trainers', 'products', 'subscription_plans',
            'promo_codes', 'promotions', 'tasks', 'trainings', 'lockers', 'expenses',
            'documents', 'support_tickets', 'guest_passes', 'feedbacks', 'sales',
            'subscriptions', 'access_logs', 'access_alarms', 'bookings',
            'staff_users', 'leads',
        ];

        foreach ($tablesRequired as $table) {
            $this->addSql(sprintf('ALTER TABLE %s ADD organization_id INT DEFAULT NULL', $table));
            $this->addSql(sprintf('UPDATE %s SET organization_id = 1', $table));
            if ($table !== 'staff_users' && $table !== 'leads') {
                $this->addSql(sprintf('ALTER TABLE %s MODIFY organization_id INT NOT NULL', $table));
            }
            $this->addSql(sprintf(
                'ALTER TABLE %s ADD CONSTRAINT FK_%s_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE',
                $table,
                $table,
            ));
            $this->addSql(sprintf('CREATE INDEX IDX_%s_org ON %s (organization_id)', $table, $table));
        }

        $this->addSql('DROP INDEX UNIQ_1483A5E9E7927C74 ON users');
        $this->addSql('CREATE UNIQUE INDEX uniq_user_org_email ON users (organization_id, email)');

        $this->addSql('ALTER TABLE club_settings ADD id INT AUTO_INCREMENT NOT NULL FIRST, ADD organization_id INT DEFAULT NULL AFTER id');
        $this->addSql('UPDATE club_settings SET organization_id = 1');
        $this->addSql('ALTER TABLE club_settings MODIFY organization_id INT NOT NULL');
        $this->addSql('ALTER TABLE club_settings DROP PRIMARY KEY, ADD PRIMARY KEY (id)');
        $this->addSql('CREATE UNIQUE INDEX uniq_club_setting_org_key ON club_settings (organization_id, setting_key)');
        $this->addSql('ALTER TABLE club_settings ADD CONSTRAINT FK_club_settings_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE');
        $this->addSql('CREATE INDEX IDX_club_settings_org ON club_settings (organization_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE club_settings DROP FOREIGN KEY FK_club_settings_org');
        $this->addSql('DROP INDEX uniq_club_setting_org_key ON club_settings');
        $this->addSql('DROP INDEX IDX_club_settings_org ON club_settings');
        $this->addSql('ALTER TABLE club_settings DROP PRIMARY KEY');
        $this->addSql('ALTER TABLE club_settings DROP COLUMN id, DROP COLUMN organization_id');
        $this->addSql('ALTER TABLE club_settings ADD PRIMARY KEY (setting_key)');

        $this->addSql('DROP INDEX uniq_user_org_email ON users');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_1483A5E9E7927C74 ON users (email)');

        $tables = [
            'clubs', 'users', 'staff_users', 'leads', 'tags', 'trainers', 'products',
            'subscription_plans', 'promo_codes', 'promotions', 'tasks', 'trainings',
            'lockers', 'expenses', 'documents', 'support_tickets', 'guest_passes',
            'feedbacks', 'sales', 'subscriptions', 'access_logs', 'access_alarms', 'bookings',
        ];

        foreach ($tables as $table) {
            $this->addSql(sprintf('ALTER TABLE %s DROP FOREIGN KEY FK_%s_org', $table, $table));
            $this->addSql(sprintf('DROP INDEX IDX_%s_org ON %s', $table, $table));
            $this->addSql(sprintf('ALTER TABLE %s DROP COLUMN organization_id', $table));
        }

        $this->addSql('DROP TABLE organizations');
    }
}
