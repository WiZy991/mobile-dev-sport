<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210910000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add clubs table for club network';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE clubs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name VARCHAR(150) NOT NULL, address VARCHAR(255) NOT NULL, phone VARCHAR(50) DEFAULT NULL, email VARCHAR(100) DEFAULT NULL, working_hours VARCHAR(100) DEFAULT NULL, latitude DOUBLE PRECISION DEFAULT NULL, longitude DOUBLE PRECISION DEFAULT NULL, amenities_json CLOB DEFAULT NULL, max_capacity INTEGER DEFAULT NULL)');
        } else {
            $this->addSql('CREATE TABLE clubs (id INT AUTO_INCREMENT NOT NULL, name VARCHAR(150) NOT NULL, address VARCHAR(255) NOT NULL, phone VARCHAR(50) DEFAULT NULL, email VARCHAR(100) DEFAULT NULL, working_hours VARCHAR(100) DEFAULT NULL, latitude DOUBLE PRECISION DEFAULT NULL, longitude DOUBLE PRECISION DEFAULT NULL, amenities_json LONGTEXT DEFAULT NULL, max_capacity INT DEFAULT NULL, PRIMARY KEY(id))');
        }
        $this->addSql("INSERT INTO clubs (name, address, phone, email, working_hours, latitude, longitude, amenities_json, max_capacity) VALUES ('FitnessClub', 'г. Москва, ул. Примерная, д. 1', '+7 (495) 123-45-67', 'info@fitnessclub.ru', 'Пн-Пт: 7:00–23:00, Сб-Вс: 9:00–21:00', 55.7558, 37.6173, 'Тренажёрный зал, Бассейн, Йога, Групповые занятия', 100)");
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS clubs');
    }
}
