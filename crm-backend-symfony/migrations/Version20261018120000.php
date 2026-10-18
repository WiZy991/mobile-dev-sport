<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use App\Migration\MigrationHelpers;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20261018120000 extends AbstractMigration
{
    use MigrationHelpers;

    public function getDescription(): string
    {
        return 'subscription_plans.details — полные условия тарифа для приложения';
    }

    public function up(Schema $schema): void
    {
        if (!$this->columnExists('subscription_plans', 'details')) {
            $this->addSql('ALTER TABLE subscription_plans ADD details LONGTEXT DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        if ($this->columnExists('subscription_plans', 'details')) {
            $this->addSql('ALTER TABLE subscription_plans DROP details');
        }
    }
}
