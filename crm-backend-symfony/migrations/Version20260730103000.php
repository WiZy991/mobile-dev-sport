<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * access_logs / access_alarms писались при PHP в UTC (naive DATETIME).
 * После APP_TIMEZONE=Asia/Vladivostok нужно хранить клубное время (UTC+10, без DST).
 */
final class Version20260730103000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Convert access_logs/access_alarms timestamps from UTC wall-clock to Asia/Vladivostok (+10h)';
    }

    public function up(Schema $schema): void
    {
        // Владивосток UTC+10 круглый год — DATE_ADD надёжнее CONVERT_TZ (нужны tz-таблицы MySQL).
        $this->addSql('UPDATE access_logs SET created_at = DATE_ADD(created_at, INTERVAL 10 HOUR)');
        $this->addSql('UPDATE access_alarms SET created_at = DATE_ADD(created_at, INTERVAL 10 HOUR)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('UPDATE access_logs SET created_at = DATE_SUB(created_at, INTERVAL 10 HOUR)');
        $this->addSql('UPDATE access_alarms SET created_at = DATE_SUB(created_at, INTERVAL 10 HOUR)');
    }
}
