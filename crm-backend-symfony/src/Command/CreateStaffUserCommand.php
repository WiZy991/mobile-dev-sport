<?php

namespace App\Command;

use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

#[AsCommand(name: 'app:create-staff-user', description: 'Создать первую учётную запись персонала CRM')]
final class CreateStaffUserCommand extends Command
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
            ->setHelp('Второй аргумент — реальный пароль для входа (не подставляйте слово «ВашПароль» из примера).')
            ->addArgument('email', InputArgument::REQUIRED)
            ->addArgument('password', InputArgument::REQUIRED, 'Пароль для /admin/login')
            ->addOption('name', null, InputOption::VALUE_REQUIRED, 'Отображаемое имя', 'Администратор');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = (string) $input->getArgument('email');
        $password = (string) $input->getArgument('password');
        $name = (string) $input->getOption('name');

        if ($this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email])) {
            $io->error('Пользователь с таким email уже существует.');

            return Command::FAILURE;
        }

        $u = (new StaffUser())
            ->setEmail($email)
            ->setName($name)
            ->setRoles(['ROLE_SUPER_ADMIN'])
            ->setIsActive(true);

        $u->setPassword($this->passwordHasher->hashPassword($u, $password));
        $this->em->persist($u);
        $this->em->flush();

        $io->success('Создан пользователь ' . $email . ' с ролью ROLE_SUPER_ADMIN.');

        return Command::SUCCESS;
    }
}
