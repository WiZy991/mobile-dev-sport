<?php

namespace App\Command;

use App\Entity\Organization;
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
            ->addOption('name', null, InputOption::VALUE_REQUIRED, 'Отображаемое имя', 'Администратор')
            ->addOption('platform', null, InputOption::VALUE_NONE, 'Оператор WorldCashFit (без организации, ROLE_PLATFORM_ADMIN)');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = (string) $input->getArgument('email');
        $password = (string) $input->getArgument('password');
        $name = (string) $input->getOption('name');
        $isPlatform = (bool) $input->getOption('platform');

        if ($this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email])) {
            $io->error('Пользователь с таким email уже существует.');

            return Command::FAILURE;
        }

        $u = (new StaffUser())
            ->setEmail($email)
            ->setName($name)
            ->setRoles($isPlatform ? ['ROLE_PLATFORM_ADMIN'] : ['ROLE_SUPER_ADMIN'])
            ->setIsActive(true);

        if (!$isPlatform) {
            $org = $this->em->getRepository(Organization::class)->findOneBy(['slug' => 'demo'])
                ?? $this->em->getRepository(Organization::class)->findOneBy([], ['id' => 'ASC']);
            if ($org === null) {
                $io->error('Нет ни одной организации. Сначала выполните миграции или app:create-organization.');

                return Command::FAILURE;
            }
            $u->setOrganization($org);
        }

        $u->setPassword($this->passwordHasher->hashPassword($u, $password));
        $this->em->persist($u);
        $this->em->flush();

        $io->success('Создан пользователь ' . $email . ' с ролью ' . ($isPlatform ? 'ROLE_PLATFORM_ADMIN' : 'ROLE_SUPER_ADMIN') . '.');

        return Command::SUCCESS;
    }
}
