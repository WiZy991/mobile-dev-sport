<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20250210500000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add lockers and locker_bookings tables';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('CREATE TABLE IF NOT EXISTS lockers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, number VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT \'available\')');
            $this->addSql('CREATE TABLE IF NOT EXISTS locker_bookings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, locker_id INTEGER NOT NULL, user_id INTEGER NOT NULL, started_at DATETIME NOT NULL, ends_at DATETIME NOT NULL, qr_token VARCHAR(64) NOT NULL, released_at DATETIME DEFAULT NULL, FOREIGN KEY (locker_id) REFERENCES lockers (id) ON DELETE CASCADE, FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_locker_bookings_locker ON locker_bookings (locker_id)');
            $this->addSql('CREATE INDEX IF NOT EXISTS IDX_locker_bookings_user ON locker_bookings (user_id)');
        } else {
            $this->addSql('CREATE TABLE IF NOT EXISTS lockers (id INT AUTO_INCREMENT NOT NULL, number VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT \'available\', PRIMARY KEY(id))');
            $this->addSql('CREATE TABLE IF NOT EXISTS locker_bookings (id INT AUTO_INCREMENT NOT NULL, locker_id INT NOT NULL, user_id INT NOT NULL, started_at DATETIME NOT NULL, ends_at DATETIME NOT NULL, qr_token VARCHAR(64) NOT NULL, released_at DATETIME DEFAULT NULL, INDEX IDX_locker_bookings_locker (locker_id), INDEX IDX_locker_bookings_user (user_id), PRIMARY KEY(id))');
            $this->addSql('ALTER TABLE locker_bookings ADD CONSTRAINT FK_locker_bookings_locker FOREIGN KEY (locker_id) REFERENCES lockers (id) ON DELETE CASCADE');
            $this->addSql('ALTER TABLE locker_bookings ADD CONSTRAINT FK_locker_bookings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE');
        }

        // Seed 20 lockers (1-20)
        for ($i = 1; $i <= 20; $i++) {
            $this->addSql("INSERT INTO lockers (number, status) VALUES ('" . $i . "', 'available')");
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE locker_bookings DROP FOREIGN KEY FK_locker_bookings_locker');
            $this->addSql('ALTER TABLE locker_bookings DROP FOREIGN KEY FK_locker_bookings_user');
        }
        $this->addSql('DROP TABLE IF EXISTS locker_bookings');
        $this->addSql('DROP TABLE IF EXISTS lockers');
    }
}
