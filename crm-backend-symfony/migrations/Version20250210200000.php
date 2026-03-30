<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210200000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add subscription_id to sales for Sale-Subscription link';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE sales ADD subscription_id INT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE sales DROP subscription_id');
    }
}
