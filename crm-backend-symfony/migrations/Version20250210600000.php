<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210600000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add event_type to access_logs for occupancy tracking';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE access_logs ADD event_type VARCHAR(10) NOT NULL DEFAULT \'entry\'');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE access_logs DROP event_type');
    }
}
