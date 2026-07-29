<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260723120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Trainer onboarding: registration status, rental, StaffUser↔Trainer; Payment trainer_rental';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("ALTER TABLE staff_users ADD registration_status VARCHAR(20) NOT NULL DEFAULT 'approved'");
        $this->addSql('ALTER TABLE staff_users ADD offer_accepted_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
        $this->addSql('ALTER TABLE staff_users ADD rental_paid_until DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\'');
        $this->addSql('ALTER TABLE staff_users ADD trainer_id INT DEFAULT NULL');
        $this->addSql('CREATE UNIQUE INDEX UNIQ_staff_users_trainer ON staff_users (trainer_id)');
        $this->addSql('ALTER TABLE staff_users ADD CONSTRAINT FK_staff_users_trainer FOREIGN KEY (trainer_id) REFERENCES trainers (id) ON DELETE SET NULL');

        $this->addSql('ALTER TABLE payments CHANGE user_id user_id INT DEFAULT NULL');
        $this->addSql('ALTER TABLE payments CHANGE subscription_plan_id subscription_plan_id INT DEFAULT NULL');
        $this->addSql('ALTER TABLE payments ADD staff_user_id INT DEFAULT NULL');
        $this->addSql('CREATE INDEX IDX_payments_staff_user ON payments (staff_user_id)');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_payments_staff_user FOREIGN KEY (staff_user_id) REFERENCES staff_users (id) ON DELETE SET NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_payments_staff_user');
        $this->addSql('DROP INDEX IDX_payments_staff_user ON payments');
        $this->addSql('ALTER TABLE payments DROP staff_user_id');
        $this->addSql('ALTER TABLE payments CHANGE user_id user_id INT NOT NULL');
        $this->addSql('ALTER TABLE payments CHANGE subscription_plan_id subscription_plan_id INT NOT NULL');

        $this->addSql('ALTER TABLE staff_users DROP FOREIGN KEY FK_staff_users_trainer');
        $this->addSql('DROP INDEX UNIQ_staff_users_trainer ON staff_users');
        $this->addSql('ALTER TABLE staff_users DROP registration_status, DROP offer_accepted_at, DROP rental_paid_until, DROP trainer_id');
    }
}
