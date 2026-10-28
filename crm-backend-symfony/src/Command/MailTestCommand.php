<?php

declare(strict_types=1);

namespace App\Command;

use App\Service\Mail\CeleryMailPublisher;
use App\Service\Notification\ClientEmailNotifier;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(name: 'app:mail:test', description: 'Отправить тестовое письмо через свой Postfix (не SMSC)')]
final class MailTestCommand extends Command
{
    public function __construct(
        private readonly ClientEmailNotifier $emailNotifier,
        private readonly CeleryMailPublisher $mailPublisher,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this->addArgument('email', InputArgument::REQUIRED, 'Куда отправить');
        $this->addOption('celery', null, InputOption::VALUE_NONE, 'Через Redis + Celery, как восстановление пароля');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = trim((string) $input->getArgument('email'));
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $io->error('Укажите корректный email.');

            return Command::FAILURE;
        }

        $subject = 'Восстановление пароля';
        $body = "Здравствуйте!\n\n"
            . "Это проверка исходящей почты клуба. Если письмо дошло — восстановление пароля будет приходить так же.\n\n"
            . "Если вы не просили это письмо, просто проигнорируйте его.\n";
        $html = '<p>Здравствуйте!</p>'
            . '<p>Это проверка исходящей почты клуба. Если письмо дошло — восстановление пароля будет приходить так же.</p>'
            . '<p>Если вы не просили это письмо, просто проигнорируйте его.</p>';
        if ($input->getOption('celery')) {
            $this->mailPublisher->enqueuePasswordReset($email, $subject, $body, $html);
            $io->success('Письмо поставлено в очередь Redis/Celery на ' . $email . '. Проверьте входящие и «Спам».');
        } else {
            $this->emailNotifier->send($email, $subject, $body);
            $io->success('Письмо поставлено в отправку на ' . $email . '. Проверьте входящие и «Спам».');
        }
        $dkim = dirname(__DIR__, 2) . '/var/mail-dkim/dkim-dns.txt';
        if (is_file($dkim)) {
            $io->writeln(trim((string) file_get_contents($dkim)));
        }

        return Command::SUCCESS;
    }
}
