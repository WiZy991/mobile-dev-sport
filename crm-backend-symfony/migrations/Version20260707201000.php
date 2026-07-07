<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260707201000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add organization subscription start date';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("ALTER TABLE organizations ADD subscription_starts_at DATETIME DEFAULT NULL COMMENT '(DC2Type:datetime_immutable)'");
        $this->addSql('UPDATE organizations SET subscription_starts_at = created_at WHERE subscription_starts_at IS NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE organizations DROP subscription_starts_at');
    }
}
