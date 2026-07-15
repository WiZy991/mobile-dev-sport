<?php

declare(strict_types=1);

namespace App\Command;

use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * @deprecated Используйте отложенные уведомления (ClientNotificationScheduler) и app:process-scheduled-notifications.
 */
#[AsCommand(name: 'app:notify-training-reminders', description: '[Устарело] Напоминания планируются при записи на тренировку')]
final class NotifyTrainingRemindersCommand extends Command
{
    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $io->warning('Команда устарела: напоминания о тренировках планируются при записи (за 60 и 10 минут до начала).');
        $io->note('Для ручной отправки наступивших: php bin/console app:process-scheduled-notifications');

        return Command::SUCCESS;
    }
}
