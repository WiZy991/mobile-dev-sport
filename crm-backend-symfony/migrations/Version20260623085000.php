<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260623085000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Add password hash for mobile user email/password auth';
    }

    public function up(Schema $schema): void
    {
        if (!$this->columnExists('users', 'password_hash')) {
            $this->addSql('ALTER TABLE users ADD password_hash VARCHAR(255) DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        if ($this->columnExists('users', 'password_hash')) {
            $this->addSql('ALTER TABLE users DROP password_hash');
        }
    }
}

