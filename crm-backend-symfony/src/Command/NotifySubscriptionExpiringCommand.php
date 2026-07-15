<?php

declare(strict_types=1);

namespace App\Command;

use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * @deprecated Используйте ClientNotificationScheduler при покупке абонемента.
 */
#[AsCommand(name: 'app:notify-subscription-expiring', description: '[Устарело] Напоминания планируются при активации абонемента')]
final class NotifySubscriptionExpiringCommand extends Command
{
    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $io->warning('Команда устарела: напоминания об окончании абонемента планируются при оплате (за 7 и 1 день).');
        $io->note('Для ручной отправки наступивших: php bin/console app:process-scheduled-notifications');

        return Command::SUCCESS;
    }
}
