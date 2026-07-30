<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Откат Version20260730103000: снова UTC wall-clock в access_logs / access_alarms.
 * Смена на Владивосток (+10h) ломала «сейчас в зале» / вход↔выход.
 */
final class Version20260730114500 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Revert access_logs/access_alarms timestamps to UTC wall-clock (−10h)';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('UPDATE access_logs SET created_at = DATE_SUB(created_at, INTERVAL 10 HOUR)');
        $this->addSql('UPDATE access_alarms SET created_at = DATE_SUB(created_at, INTERVAL 10 HOUR)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('UPDATE access_logs SET created_at = DATE_ADD(created_at, INTERVAL 10 HOUR)');
        $this->addSql('UPDATE access_alarms SET created_at = DATE_ADD(created_at, INTERVAL 10 HOUR)');
    }
}
