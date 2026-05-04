<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\Club;
use Doctrine\DBAL\Platforms\SQLitePlatform;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Три клуба при регистрации в Android-приложении (clubId = clubs.id).
 *
 * @see FitnessClub/app/src/main/java/com/fitnessclub/app/ui/screens/auth/RegistrationVenues.kt
 */
#[AsCommand(
    name: 'app:clubs:sync-registration-venues',
    description: 'Синхронизировать clubs id 1–3 с карточками регистрации в приложении; опционально токены шлюза',
)]
final class SyncRegistrationVenuesCommand extends Command
{
    /** @var list<array{0: int, 1: string, 2: string}> */
    private const VENUES = [
        [1, 'ТЦ Формат', 'ул. Центральная, 18, 2 этаж'],
        [2, 'ТЦ Новый де Фриз', 'ул. Купера, 2, 2 этаж'],
        [3, 'ул. Купера, 2', 'Основной зал'],
    ];

    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this
            ->addOption('with-gateway-tokens', null, InputOption::VALUE_NONE, 'Сгенерировать токен шлюза там, где его ещё нет')
            ->addOption('rotate-gateway-tokens', null, InputOption::VALUE_NONE, 'Перегенерировать токены у всех трёх (старые config.ini перестанут работать)')
            ->setHelp(
                'PERCo (URL, логин, пароль, device id) по-прежнему задаются в CRM /admin/franchise/{id} или только в config.ini шлюза — эта команда не трогает их.'
            );
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $conn = $this->em->getConnection();
        $platform = $conn->getDatabasePlatform();
        $isSqlite = $platform instanceof SQLitePlatform;

        foreach (self::VENUES as [$id, $name, $address]) {
            if ($isSqlite) {
                $conn->executeStatement(
                    'INSERT INTO clubs (id, name, address, max_capacity, perco_verify_ssl)
                     VALUES (?, ?, ?, 100, 1)
                     ON CONFLICT(id) DO UPDATE SET
                       name = excluded.name,
                       address = excluded.address,
                       max_capacity = excluded.max_capacity',
                    [$id, $name, $address]
                );
            } else {
                $conn->executeStatement(
                    'INSERT INTO clubs (id, name, address, max_capacity, perco_verify_ssl)
                     VALUES (?, ?, ?, 100, 1)
                     ON DUPLICATE KEY UPDATE
                       name = VALUES(name),
                       address = VALUES(address),
                       max_capacity = VALUES(max_capacity)',
                    [$id, $name, $address]
                );
            }
        }

        $this->em->clear();

        $rotate = (bool) $input->getOption('rotate-gateway-tokens');
        $fillMissing = (bool) $input->getOption('with-gateway-tokens');
        if ($rotate && !$fillMissing) {
            $fillMissing = true;
        }

        $repo = $this->em->getRepository(Club::class);
        $printed = [];

        foreach (self::VENUES as [$id, $name]) {
            $club = $repo->find($id);
            if (!$club instanceof Club) {
                $io->error("Клуб с id={$id} не найден после upsert.");

                return Command::FAILURE;
            }

            if ($rotate) {
                $club->setGatewayToken($this->newGatewayToken());
            } elseif ($fillMissing && $club->getGatewayToken() === null) {
                $club->setGatewayToken($this->newGatewayToken());
            }

            $token = $club->getGatewayToken();
            $printed[] = [
                'id' => (string) $id,
                'name' => $name,
                'gateway_token' => $token ?? '— (сгенерируйте в /admin/franchise или запустите с --with-gateway-tokens)',
            ];
        }

        $this->em->flush();

        $io->success('Клубы id 1–3 синхронизированы с приложением (название + адрес).');
        $io->table(array_keys($printed[0]), $printed);

        return Command::SUCCESS;
    }

    private function newGatewayToken(): string
    {
        return bin2hex(random_bytes(24));
    }
}
