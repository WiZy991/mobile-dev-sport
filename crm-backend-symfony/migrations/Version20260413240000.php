<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260413240000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'User preferred club (CRM filter/export) + seed clubs ТЦ Формат / ТЦ Новый де фриз';
    }

    public function up(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if ($isSqlite) {
            $this->addSql('ALTER TABLE users ADD COLUMN club_id INTEGER DEFAULT NULL');
            $this->addSql('CREATE INDEX IDX_users_club_id ON users (club_id)');
        } else {
            $this->addSql('ALTER TABLE users ADD club_id INT DEFAULT NULL');
            $this->addSql('CREATE INDEX IDX_users_club_id ON users (club_id)');
            $this->addSql('ALTER TABLE users ADD CONSTRAINT FK_users_club FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE SET NULL');
        }

        $conn = $this->connection;
        foreach ([
            ['ТЦ Формат', 'ул. Купера, 2'],
            ['ТЦ Новый де фриз', 'ул. Купера, 2'],
        ] as [$name, $address]) {
            $exists = (int) $conn->fetchOne('SELECT COUNT(*) FROM clubs WHERE name = ?', [$name]);
            if ($exists === 0) {
                $conn->insert('clubs', [
                    'name' => $name,
                    'address' => $address,
                    'phone' => null,
                    'email' => null,
                    'working_hours' => null,
                    'amenities_json' => null,
                    'max_capacity' => null,
                    'latitude' => null,
                    'longitude' => null,
                ]);
            }
        }
    }

    public function down(Schema $schema): void
    {
        $isSqlite = $this->connection->getDatabasePlatform() instanceof SQLitePlatform;
        if (!$isSqlite) {
            $this->addSql('ALTER TABLE users DROP FOREIGN KEY FK_users_club');
            $this->addSql('DROP INDEX IDX_users_club_id ON users');
            $this->addSql('ALTER TABLE users DROP COLUMN club_id');
        } else {
            $this->addSql('DROP INDEX IDX_users_club_id');
            $this->addSql('ALTER TABLE users DROP COLUMN club_id');
        }
    }
}
