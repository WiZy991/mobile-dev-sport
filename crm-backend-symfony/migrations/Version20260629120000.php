<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260629120000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add payments table for Alfa Bank internet acquiring';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('CREATE TABLE payments (
            id INT AUTO_INCREMENT NOT NULL,
            user_id INT NOT NULL,
            subscription_plan_id INT NOT NULL,
            promo_code_id INT DEFAULT NULL,
            subscription_id INT DEFAULT NULL,
            sale_id INT DEFAULT NULL,
            type VARCHAR(30) NOT NULL,
            amount_kopecks INT NOT NULL,
            currency INT NOT NULL,
            discount_amount DOUBLE PRECISION DEFAULT 0 NOT NULL,
            status VARCHAR(20) NOT NULL,
            order_number VARCHAR(64) NOT NULL,
            alfa_order_id VARCHAR(64) DEFAULT NULL,
            payment_url VARCHAR(512) DEFAULT NULL,
            payment_way VARCHAR(30) DEFAULT NULL,
            expires_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            paid_at DATETIME DEFAULT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            failure_reason VARCHAR(255) DEFAULT NULL,
            raw_callback JSON DEFAULT NULL,
            created_at DATETIME NOT NULL COMMENT \'(DC2Type:datetime_immutable)\',
            UNIQUE INDEX uniq_payment_order_number (order_number),
            UNIQUE INDEX uniq_payment_alfa_order_id (alfa_order_id),
            INDEX idx_payment_user_status (user_id, status),
            INDEX IDX_65D29B32A76ED395 (user_id),
            INDEX IDX_65D29B329B8CE200 (subscription_plan_id),
            INDEX IDX_65D29B322FAE9625 (promo_code_id),
            UNIQUE INDEX UNIQ_65D29B329A1887DC (subscription_id),
            UNIQUE INDEX UNIQ_65D29B324A7E4868 (sale_id),
            PRIMARY KEY(id)
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE `utf8mb4_unicode_ci` ENGINE = InnoDB');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_65D29B32A76ED395 FOREIGN KEY (user_id) REFERENCES users (id)');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_65D29B329B8CE200 FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plans (id)');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_65D29B322FAE9625 FOREIGN KEY (promo_code_id) REFERENCES promo_codes (id)');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_65D29B329A1887DC FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)');
        $this->addSql('ALTER TABLE payments ADD CONSTRAINT FK_65D29B324A7E4868 FOREIGN KEY (sale_id) REFERENCES sales (id)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_65D29B32A76ED395');
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_65D29B329B8CE200');
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_65D29B322FAE9625');
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_65D29B329A1887DC');
        $this->addSql('ALTER TABLE payments DROP FOREIGN KEY FK_65D29B324A7E4868');
        $this->addSql('DROP TABLE payments');
    }
}
