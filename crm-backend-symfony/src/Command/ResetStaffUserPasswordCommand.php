<?php

namespace App\Command;

use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

#[AsCommand(
    name: 'app:reset-staff-password',
    description: 'Задать новый пароль существующей учётной записи CRM (staff_users)',
)]
final class ResetStaffUserPasswordCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly UserPasswordHasherInterface $passwordHasher,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this
            ->addArgument('email', InputArgument::REQUIRED, 'Email из staff_users')
            ->addArgument('password', InputArgument::REQUIRED, 'Новый пароль (это значение, не шаблон)');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = (string) $input->getArgument('email');
        $plain = (string) $input->getArgument('password');

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if (!$user instanceof StaffUser) {
            $io->error('Пользователь не найден: ' . $email);

            return Command::FAILURE;
        }

        $user->setPassword($this->passwordHasher->hashPassword($user, $plain));
        $this->em->flush();

        $io->success('Пароль для ' . $email . ' обновлён. Входите с этим паролем на /admin/login');

        return Command::SUCCESS;
    }
}
