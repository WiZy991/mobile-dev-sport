<?php

declare(strict_types=1);

namespace App\Command;

use App\Service\Notification\ScheduledNotificationProcessor;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Ручной запуск обработки отложенных уведомлений (для отладки).
 * В проде напоминания уходят без cron — при HTTP-запросах к API/админке (см. ScheduledNotificationRequestSubscriber).
 */
#[AsCommand(
    name: 'app:process-scheduled-notifications',
    description: 'Отправить наступившие отложенные клиентские уведомления',
)]
final class ProcessScheduledNotificationsCommand extends Command
{
    public function __construct(
        private readonly ScheduledNotificationProcessor $processor,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $sent = $this->processor->processDue();
        $io->success(sprintf('Отправлено отложенных уведомлений: %d', $sent));

        return Command::SUCCESS;
    }
}
