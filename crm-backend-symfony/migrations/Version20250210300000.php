<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210300000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add tags and user_tags for client tags';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(50) NOT NULL, color VARCHAR(7) DEFAULT NULL)');
            $this->addSql('CREATE TABLE IF NOT EXISTS user_tags (user_id INTEGER NOT NULL, tag_id INTEGER NOT NULL, PRIMARY KEY(user_id, tag_id), FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE, FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS tags (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(50) NOT NULL, color VARCHAR(7) DEFAULT NULL, PRIMARY KEY(id))');
            $this->addSql('CREATE TABLE IF NOT EXISTS user_tags (user_id INT NOT NULL, tag_id INT NOT NULL, INDEX IDX_user_tags_user (user_id), INDEX IDX_user_tags_tag (tag_id), PRIMARY KEY(user_id, tag_id))');
            $this->addSql('ALTER TABLE user_tags ADD CONSTRAINT FK_user_tags_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
            $this->addSql('ALTER TABLE user_tags ADD CONSTRAINT FK_user_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE user_tags DROP FOREIGN KEY FK_user_tags_user');
            $this->addSql('ALTER TABLE user_tags DROP FOREIGN KEY FK_user_tags_tag');
        }
        $this->addSql('DROP TABLE user_tags');
        $this->addSql('DROP TABLE tags');
    }
}
