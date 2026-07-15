<?php

declare(strict_types=1);

namespace App\Migrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260715120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Отложенные клиентские уведомления (без cron): напоминания о тренировках и абонементах';
    }

    public function up(Schema $schema): void
    {
        if ($this->connection->getDatabasePlatform()->getName() === 'postgresql') {
            $this->addSql(<<<'SQL'
                CREATE TABLE scheduled_client_notifications (
                    id SERIAL NOT NULL,
                    user_id INT NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    title VARCHAR(150) NOT NULL,
                    body TEXT NOT NULL,
                    reference_id VARCHAR(120) NOT NULL,
                    notify_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'pending',
                    created_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                    sent_at TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT NULL,
                    PRIMARY KEY(id),
                    CONSTRAINT FK_scheduled_client_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
                )
                SQL);
            $this->addSql('CREATE UNIQUE INDEX UNIQ_scheduled_client_notif_ref ON scheduled_client_notifications (reference_id)');
            $this->addSql('CREATE INDEX idx_scheduled_client_notif_due ON scheduled_client_notifications (status, notify_at)');

            return;
        }

        $this->addSql(<<<'SQL'
            CREATE TABLE scheduled_client_notifications (
                id INT AUTO_INCREMENT NOT NULL,
                user_id INT NOT NULL,
                type VARCHAR(50) NOT NULL,
                title VARCHAR(150) NOT NULL,
                body TEXT NOT NULL,
                reference_id VARCHAR(120) NOT NULL,
                notify_at DATETIME NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'pending',
                created_at DATETIME NOT NULL,
                sent_at DATETIME DEFAULT NULL,
                INDEX IDX_scheduled_client_notif_user (user_id),
                INDEX idx_scheduled_client_notif_due (status, notify_at),
                UNIQUE INDEX UNIQ_scheduled_client_notif_ref (reference_id),
                PRIMARY KEY(id),
                CONSTRAINT FK_scheduled_client_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB
            SQL);
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE scheduled_client_notifications');
    }
}
