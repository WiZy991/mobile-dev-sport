<?php

declare(strict_types=1);

namespace App\Command;

use App\Service\Reports\AccessLogStaffLinker;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:access-logs-backfill-staff',
    description: 'Привязать старые входы тренеров (STAFF QR / Wiegand) к staff_users',
)]
final class AccessLogsBackfillStaffCommand extends Command
{
    public function __construct(
        private readonly AccessLogStaffLinker $linker,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $result = $this->linker->backfillDatabase();
        $io->success(sprintf(
            'Привязано: STAFF QR = %d, Wiegand = %d',
            $result['staff_qr'],
            $result['wiegand'],
        ));

        return Command::SUCCESS;
    }
}
