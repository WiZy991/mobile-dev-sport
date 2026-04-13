<?php

declare(strict_types=1);

namespace App\Command;

use Doctrine\DBAL\Connection;
use Doctrine\DBAL\DriverManager;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;
use Symfony\Component\DependencyInjection\Attribute\Autowire;

#[AsCommand(
    name: 'app:data:migrate-sqlite-to-mysql',
    description: 'Копирует данные из SQLite (старый var/data_*.db) в текущую БД из DATABASE_URL (ожидается MySQL/MariaDB)',
)]
final class MigrateSqliteToMysqlCommand extends Command
{
    private const EXCLUDED_TABLES = [
        'doctrine_migration_versions',
    ];

    public function __construct(
        private readonly Connection $targetConnection,
        #[Autowire('%kernel.project_dir%')]
        private readonly string $projectDir,
        #[Autowire('%kernel.environment%')]
        private readonly string $environment,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $defaultSqlitePath = \sprintf('%s/var/data_%s.db', $this->projectDir, $this->environment);
        $this
            ->addOption(
                'sqlite-path',
                null,
                InputOption::VALUE_REQUIRED,
                'Путь к файлу SQLite (абсолютный или относительно корня проекта)',
                $defaultSqlitePath,
            )
            ->addOption('dry-run', null, InputOption::VALUE_NONE, 'Только показать таблицы и количество строк, без изменений в MySQL')
            ->addOption('force', null, InputOption::VALUE_NONE, 'Очистить совпадающие таблицы в MySQL и импортировать данные из SQLite');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        if ($this->targetConnection->getDatabasePlatform() instanceof SQLitePlatform) {
            $io->error('Текущий DATABASE_URL указывает на SQLite. Укажите MySQL в .env.local и выполните doctrine:migrations:migrate, затем запустите эту команду снова.');

            return Command::FAILURE;
        }

        $sqlitePath = (string) $input->getOption('sqlite-path');
        $sqlitePath = str_replace('\\', '/', $sqlitePath);
        if (!$this->isAbsolutePath($sqlitePath)) {
            $sqlitePath = $this->projectDir.'/'.ltrim($sqlitePath, '/');
        }

        if (!is_file($sqlitePath)) {
            $io->error(\sprintf('Файл SQLite не найден: %s', $sqlitePath));

            return Command::FAILURE;
        }

        $source = DriverManager::getConnection([
            'driver' => 'pdo_sqlite',
            'path' => $sqlitePath,
        ]);

        if (!$source->getDatabasePlatform() instanceof SQLitePlatform) {
            $io->error('Источник не распознан как SQLite.');

            return Command::FAILURE;
        }

        $targetSm = $this->targetConnection->createSchemaManager();
        $sourceSm = $source->createSchemaManager();

        $targetTables = $this->filterAppTables($targetSm->listTableNames());
        $sourceTables = $this->filterAppTables($sourceSm->listTableNames());

        $common = array_values(array_intersect($targetTables, $sourceTables));
        sort($common);

        $onlyTarget = array_diff($targetTables, $sourceTables);
        $onlySource = array_diff($sourceTables, $targetTables);

        if ($onlyTarget !== []) {
            $io->note('Таблицы есть только в MySQL (останутся пустыми после TRUNCATE или без изменений): '.implode(', ', $onlyTarget));
        }
        if ($onlySource !== []) {
            $io->warning('Таблицы есть только в SQLite (не будут перенесены): '.implode(', ', $onlySource));
        }

        $io->title('Перенос данных SQLite → MySQL');
        $io->writeln(\sprintf('<info>Источник:</info> %s', $sqlitePath));

        $counts = [];
        foreach ($common as $table) {
            $n = (int) $source->fetchOne(\sprintf('SELECT COUNT(*) FROM %s', $this->quoteIdent($source, $table)));
            $counts[$table] = $n;
            $io->writeln(\sprintf('  %s — %d строк', $table, $n));
        }

        if ($input->getOption('dry-run')) {
            $io->success('Dry-run: изменений в MySQL не было.');

            return Command::SUCCESS;
        }

        if (!$input->getOption('force')) {
            $io->warning('Добавьте --force для TRUNCATE целевых таблиц и копирования данных, или --dry-run для проверки.');

            return Command::FAILURE;
        }

        $this->targetConnection->executeStatement('SET FOREIGN_KEY_CHECKS=0');

        try {
            foreach ($common as $table) {
                $this->targetConnection->executeStatement(
                    \sprintf('TRUNCATE TABLE %s', $this->quoteIdent($this->targetConnection, $table)),
                );
            }

            foreach ($common as $table) {
                $this->copyTable($source, $this->targetConnection, $table, $io);
            }
        } finally {
            $this->targetConnection->executeStatement('SET FOREIGN_KEY_CHECKS=1');
        }

        $source->close();

        $io->success('Данные скопированы. Проверьте приложение и при необходимости сбросьте кэш: bin/console cache:clear.');

        return Command::SUCCESS;
    }

    /**
     * @param list<string> $tables
     *
     * @return list<string>
     */
    private function filterAppTables(array $tables): array
    {
        $out = [];
        foreach ($tables as $t) {
            if (\in_array($t, self::EXCLUDED_TABLES, true)) {
                continue;
            }
            $out[] = $t;
        }

        return $out;
    }

    private function quoteIdent(Connection $conn, string $name): string
    {
        return $conn->getDatabasePlatform()->quoteSingleIdentifier($name);
    }

    private function copyTable(Connection $source, Connection $target, string $table, SymfonyStyle $io): void
    {
        $sourceSm = $source->createSchemaManager();
        $targetSm = $target->createSchemaManager();

        $sourceColNames = array_map(static fn ($c) => $c->getName(), $sourceSm->listTableColumns($table));
        $targetColNames = array_map(static fn ($c) => $c->getName(), $targetSm->listTableColumns($table));
        $columns = array_values(array_intersect($sourceColNames, $targetColNames));

        if ($columns === []) {
            $io->warning(\sprintf('Пропуск %s: нет общих колонок между SQLite и MySQL.', $table));

            return;
        }

        $colList = implode(', ', array_map(fn (string $c): string => $this->quoteIdent($source, $c), $columns));
        $rows = $source->fetchAllAssociative(
            \sprintf('SELECT %s FROM %s', $colList, $this->quoteIdent($source, $table)),
        );

        foreach ($rows as $row) {
            $payload = [];
            foreach ($columns as $col) {
                if (!\array_key_exists($col, $row)) {
                    continue;
                }
                $payload[$col] = $row[$col];
            }
            if ($payload === []) {
                continue;
            }
            $target->insert($table, $payload);
        }
    }

    private function isAbsolutePath(string $path): bool
    {
        if (str_starts_with($path, '/')) {
            return true;
        }

        return (bool) preg_match('#^[A-Za-z]:/#', $path);
    }
}
