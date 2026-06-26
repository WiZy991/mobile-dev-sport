<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260626120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add referral_source / referral_source_other to users (registration survey "How did you hear about us")';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD referral_source VARCHAR(50) DEFAULT NULL');
        $this->addSql('ALTER TABLE users ADD referral_source_other VARCHAR(255) DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP referral_source');
        $this->addSql('ALTER TABLE users DROP referral_source_other');
    }
}
