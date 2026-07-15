<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260714210000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'User notification preferences for mobile app settings';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users ADD notify_push_enabled TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE users ADD notify_email_enabled TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE users ADD notify_training_reminders TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE users ADD notify_schedule_changes TINYINT(1) DEFAULT 1 NOT NULL');
        $this->addSql('ALTER TABLE users ADD notify_promo TINYINT(1) DEFAULT 0 NOT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE users DROP notify_push_enabled');
        $this->addSql('ALTER TABLE users DROP notify_email_enabled');
        $this->addSql('ALTER TABLE users DROP notify_training_reminders');
        $this->addSql('ALTER TABLE users DROP notify_schedule_changes');
        $this->addSql('ALTER TABLE users DROP notify_promo');
    }
}
