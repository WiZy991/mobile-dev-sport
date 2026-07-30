<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Убрать захардкоженный демо-баннер «СКИДКА 20%!» из настроек клуба.
 * Главный экран должен брать баннеры только из раздела «Акции».
 */
final class Version20260730111000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Clear default promo_home_title/subtitle (СКИДКА 20%) from club_settings';
    }

    public function up(Schema $schema): void
    {
        $this->addSql("UPDATE club_settings SET setting_value = NULL WHERE setting_key = 'promo_home_title' AND setting_value IN ('СКИДКА 20%!', 'СКИДКА 20%')");
        $this->addSql("UPDATE club_settings SET setting_value = NULL WHERE setting_key = 'promo_home_subtitle' AND setting_value = 'на все карты 12 и 6 месяцев'");
    }

    public function down(Schema $schema): void
    {
        // no-op: не возвращаем демо-текст
    }
}
