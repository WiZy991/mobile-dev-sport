<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Абонемент привязан к клубу: на турникете с токеном клуба A принимаются только абонементы с club_id=A
 * или club_id NULL (наследие: «любой клуб»).
 */
final class Version20260519140000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Subscriptions: optional club_id (per-club validity at gateway).';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($isSqlite) {
            $this->addSql('ALTER TABLE subscriptions ADD COLUMN club_id INTEGER DEFAULT NULL');
            $this->addSql('CREATE INDEX IDX_subscriptions_club ON subscriptions (club_id)');
        } else {
            $this->addSql('ALTER TABLE subscriptions ADD club_id INT DEFAULT NULL');
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
