<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260708120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add apple_id to users for Sign in with Apple (optional backend auth)';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD apple_id VARCHAR(128) DEFAULT NULL');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_1483A5E9FB88A929 ON users (apple_id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP INDEX UNIQ_1483A5E9FB88A929 ON users');
        $this->addSql('ALTER TABLE users DROP apple_id');
    }
}
