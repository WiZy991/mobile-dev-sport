<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260519140000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'Subscriptions: optional club_id (per-club validity at gateway).';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            if (!$this->columnExists('subscriptions', 'club_id')) {
                $this->addSql('ALTER TABLE subscriptions ADD COLUMN club_id INTEGER DEFAULT NULL');
            }
            if (!$this->indexExists('subscriptions', 'IDX_subscriptions_club')) {
                $this->addSql('CREATE INDEX IDX_subscriptions_club ON subscriptions (club_id)');
            }

            return;
        }

        if (!$this->columnExists('subscriptions', 'club_id')) {
            $this->addSql('ALTER TABLE subscriptions ADD club_id INT DEFAULT NULL');
        }
        if (!$this->foreignKeyExists('subscriptions', 'FK_subscriptions_valid_club')) {
            $this->addSql('ALTER TABLE subscriptions ADD CONSTRAINT FK_subscriptions_valid_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql('DROP INDEX IDX_subscriptions_club');
            $this->addSql('ALTER TABLE subscriptions DROP COLUMN club_id');
        } else {
            $this->addSql('ALTER TABLE subscriptions DROP FOREIGN KEY FK_subscriptions_valid_club');
            $this->addSql('ALTER TABLE subscriptions DROP club_id');
        }
    }
}
