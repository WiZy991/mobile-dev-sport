<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/** Срок аренды тренера в платеже + автор тикета поддержки (staff). */
final class Version20260821190000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'payments.duration_months + support_tickets.staff_user_id';
    }

    public function up(Schema $schema): void
    {
        $sqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;

        if ($this->tableExists('payments') && !$this->columnExists('payments', 'duration_months')) {
            if ($sqlite) {
                $this->addSql('ALTER TABLE payments ADD COLUMN duration_months INTEGER DEFAULT 1');
            } else {
                $this->addSql('ALTER TABLE payments ADD duration_months INT DEFAULT 1');
            }
        }

        if ($this->tableExists('support_tickets') && !$this->columnExists('support_tickets', 'staff_user_id')) {
            if ($sqlite) {
                $this->addSql('ALTER TABLE support_tickets ADD COLUMN staff_user_id INTEGER DEFAULT NULL');
            } else {
                $this->addSql('ALTER TABLE support_tickets ADD staff_user_id INT DEFAULT NULL');
                $this->addSql('ALTER TABLE support_tickets ADD CONSTRAINT FK_support_tickets_staff_user FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE SET NULL');
                $this->addSql('CREATE INDEX IDX_support_tickets_staff_user ON support_tickets (staff_user_id)');
            }
        }
    }

    public function down(Schema $schema): void
    {
        if ($this->connection->getDatabasePlatform() instanceof SQLitePlatform) {
            return;
        }
        if ($this->tableExists('support_tickets') && $this->columnExists('support_tickets', 'staff_user_id')) {
            $this->addSql('ALTER TABLE support_tickets DROP FOREIGN KEY FK_support_tickets_staff_user');
            $this->addSql('DROP INDEX IDX_support_tickets_staff_user ON support_tickets');
            $this->addSql('ALTER TABLE support_tickets DROP staff_user_id');
        }
        if ($this->tableExists('payments') && $this->columnExists('payments', 'duration_months')) {
            $this->addSql('ALTER TABLE payments DROP duration_months');
        }
    }
}
