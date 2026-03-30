<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210100000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add source column to leads table';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE leads ADD source VARCHAR(50) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE leads DROP source');
    }
}
