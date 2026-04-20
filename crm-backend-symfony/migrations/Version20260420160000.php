<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Три точки для регистрации в приложении: зал на Купера 2 и два ТЦ.
 */
final class Version20260420160000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Clubs: адреса ТЦ Формат / ТЦ Новый де фриз + отдельный зал ул. Купера, 2';
    }

    public function up(Schema $schema): void
    {
        $conn = $this->connection;

        $conn->executeStatement(
            "UPDATE clubs SET address = ? WHERE name = ?",
            ['ул. Центральная, 18, 2 этаж', 'ТЦ Формат']
        );
        $conn->executeStatement(
            "UPDATE clubs SET address = ? WHERE name = ?",
            ['ул. Купера, 2, 2 этаж', 'ТЦ Новый де фриз']
        );

        $name = 'ул. Купера, 2';
        $exists = (int) $conn->fetchOne('SELECT COUNT(*) FROM clubs WHERE name = ?', [$name]);
        if ($exists === 0) {
            $conn->insert('clubs', [
                'name' => $name,
                'address' => 'ул. Купера, 2',
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

    public function down(Schema $schema): void
    {
        $conn = $this->connection;
        $conn->executeStatement("DELETE FROM clubs WHERE name = ?", ['ул. Купера, 2']);
        $conn->executeStatement(
            "UPDATE clubs SET address = ? WHERE name = ?",
            ['ул. Купера, 2', 'ТЦ Формат']
        );
        $conn->executeStatement(
            "UPDATE clubs SET address = ? WHERE name = ?",
            ['ул. Купера, 2', 'ТЦ Новый де фриз']
        );
    }
}
