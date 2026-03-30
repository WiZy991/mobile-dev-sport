<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210110000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add user_id to documents for user-uploaded documents';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('ALTER TABLE documents ADD COLUMN user_id INTEGER DEFAULT NULL REFERENCES users(id)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_documents_user ON documents (user_id)');
        } else {
            $this->addSql('ALTER TABLE documents ADD user_id INT DEFAULT NULL');
            $this->addSql('CREATE INDEX IDX_documents_user ON documents (user_id)');
            $this->addSql('ALTER TABLE documents ADD CONSTRAINT FK_documents_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('DROP INDEX IF EXISTS IDX_documents_user');
            $this->addSql('ALTER TABLE documents DROP COLUMN user_id');
        } else {
            $this->addSql('ALTER TABLE documents DROP FOREIGN KEY FK_documents_user');
            $this->addSql('DROP INDEX IDX_documents_user ON documents');
            $this->addSql('ALTER TABLE documents DROP user_id');
        }
    }
}
