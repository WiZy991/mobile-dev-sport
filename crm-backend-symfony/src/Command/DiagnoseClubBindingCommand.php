<?php

declare(strict_types=1);

namespace App\Command;

use Doctrine\DBAL\ArrayParameterType;
use Doctrine\DBAL\Connection;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * «Клиент видит не свой зал»: показывает, что реально лежит в БД —
 * залы с их id (их же присылает приложение как club_id), настройки сети
 * (их подставляет /club/info, когда у клиента зала нет) и зал последних регистраций.
 *
 * Только чтение, ничего не меняет.
 */
#[AsCommand(
    name: 'app:clubs:diagnose',
    description: 'Диагностика привязки клиентов к залам (залы, настройки сети, последние регистрации)',
)]
final class DiagnoseClubBindingCommand extends Command
{
    public function __construct(
        private readonly Connection $connection,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this->addOption(
            'clients',
            null,
            InputOption::VALUE_REQUIRED,
            'Сколько последних клиентов показать',
            '15',
        );
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        $io->section('Залы (clubs) — id совпадает с club_id из приложения');
        $io->table(
            ['id', 'name', 'address', 'org', 'show_in_app', 'max_capacity'],
            array_map(
                static fn (array $r): array => [
                    (string) $r['id'],
                    (string) ($r['name'] ?? ''),
                    (string) ($r['address'] ?? ''),
                    (string) ($r['organization_id'] ?? '—'),
                    (string) ($r['show_in_app'] ?? '—'),
                    (string) ($r['max_capacity'] ?? '—'),
                ],
                $this->fetchClubs(),
            ),
        );
        $io->comment('В приложении карточки регистрации жёстко привязаны к id: 1 = ТЦ Формат, 2 = ТЦ Новый де Фриз, 11 = ТЦ Седанка Сити.');

        $io->section('Настройки сети (club_settings)');
        $io->table(
            ['org', 'setting_key', 'value'],
            array_map(
                static fn (array $r): array => [
                    (string) ($r['organization_id'] ?? '—'),
                    (string) $r['setting_key'],
                    $r['setting_value'] !== null ? (string) $r['setting_value'] : '— не задано —',
                ],
                $this->fetchSettings(['name', 'address']),
            ),
        );
        $io->comment('Эти значения приложение показывает как «ваш зал», если у клиента зал не привязан.');

        $limit = max(1, (int) $input->getOption('clients'));
        $io->section(sprintf('Последние %d клиентов', $limit));
        $rows = $this->fetchRecentClients($limit);
        $io->table(
            ['user id', 'email', 'создан', 'club_id', 'зал'],
            array_map(
                static fn (array $r): array => [
                    (string) $r['id'],
                    (string) ($r['email'] ?? ''),
                    (string) ($r['created_at'] ?? ''),
                    $r['club_id'] !== null ? (string) $r['club_id'] : '— НЕТ ЗАЛА —',
                    (string) ($r['club_name'] ?? ''),
                ],
                $rows,
            ),
        );

        $withoutClub = 0;
        foreach ($rows as $r) {
            if ($r['club_id'] === null) {
                ++$withoutClub;
            }
        }
        if ($withoutClub > 0) {
            $io->warning(sprintf(
                '%d из %d последних клиентов без зала: им приложение покажет заполненность всей сети и адрес из настроек сети.',
                $withoutClub,
                \count($rows),
            ));
        } else {
            $io->success('У последних клиентов зал привязан — сравните название зала с тем, что человек выбирал при регистрации.');
        }

        $io->section('Журнал проходов за сутки');
        $io->table(
            ['club_id', 'зал', 'событий'],
            array_map(
                static fn (array $r): array => [
                    $r['club_id'] !== null ? (string) $r['club_id'] : '— NULL —',
                    (string) ($r['club_name'] ?? ''),
                    (string) $r['cnt'],
                ],
                $this->fetchAccessLogClubs(),
            ),
        );
        $io->comment('club_id = NULL в журнале не попадёт в счётчик заполненности зала: проверьте токен шлюза в config.ini этого зала.');

        return Command::SUCCESS;
    }

    /** @return list<array<string, mixed>> */
    private function fetchClubs(): array
    {
        $columns = ['id', 'name', 'address'];
        foreach (['organization_id', 'show_in_app', 'max_capacity'] as $optional) {
            if ($this->columnExists('clubs', $optional)) {
                $columns[] = $optional;
            }
        }

        return $this->connection->fetchAllAssociative(
            sprintf('SELECT %s FROM clubs ORDER BY id ASC', implode(', ', $columns))
        );
    }

    /**
     * @param list<string> $keys
     *
     * @return list<array<string, mixed>>
     */
    private function fetchSettings(array $keys): array
    {
        $orgColumn = $this->columnExists('club_settings', 'organization_id')
            ? 'organization_id'
            : 'NULL AS organization_id';

        return $this->connection->fetchAllAssociative(
            sprintf(
                'SELECT %s, setting_key, setting_value FROM club_settings WHERE setting_key IN (?) ORDER BY setting_key ASC',
                $orgColumn,
            ),
            [$keys],
            [ArrayParameterType::STRING],
        );
    }

    /** @return list<array<string, mixed>> */
    private function fetchRecentClients(int $limit): array
    {
        return $this->connection->fetchAllAssociative(
            'SELECT u.id, u.email, u.created_at, u.club_id, c.name AS club_name
             FROM users u
             LEFT JOIN clubs c ON c.id = u.club_id
             ORDER BY u.id DESC
             LIMIT ' . $limit
        );
    }

    /** @return list<array<string, mixed>> */
    private function fetchAccessLogClubs(): array
    {
        return $this->connection->fetchAllAssociative(
            'SELECT al.club_id, c.name AS club_name, COUNT(*) AS cnt
             FROM access_logs al
             LEFT JOIN clubs c ON c.id = al.club_id
             WHERE al.created_at >= ?
             GROUP BY al.club_id, c.name
             ORDER BY cnt DESC',
            [(new \DateTimeImmutable('-1 day'))->format('Y-m-d H:i:s')],
        );
    }

    private function columnExists(string $table, string $column): bool
    {
        try {
            foreach ($this->connection->createSchemaManager()->listTableColumns($table) as $col) {
                if (strtolower($col->getName()) === strtolower($column)) {
                    return true;
                }
            }
        } catch (\Throwable) {
        }

        return false;
    }
}
