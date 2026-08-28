<?php

declare(strict_types=1);

namespace App\Command;

use App\Service\Reports\OccupancyService;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Авто-выход клиентов, которые в зале дольше 3 часов без QR-выхода.
 * Запускается воркером рядом с app:process-scheduled-notifications.
 */
#[AsCommand(
    name: 'app:occupancy-auto-exit',
    description: 'Отметить выход тем, кто в зале дольше 3 часов',
)]
final class OccupancyAutoExitCommand extends Command
{
    public function __construct(
        private readonly OccupancyService $occupancy,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $n = $this->occupancy->autoExitStaleInside();
        if ($n > 0) {
            $io->success(sprintf('Авто-выход (3 ч): %d чел.', $n));
        }

        return Command::SUCCESS;
    }
}
