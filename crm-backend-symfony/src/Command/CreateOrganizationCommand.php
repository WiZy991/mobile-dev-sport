<?php

declare(strict_types=1);

namespace App\Command;

use App\Service\Tenant\OrganizationProvisioner;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:create-organization',
    description: 'Создать организацию-лицензиата с первым клубом и администратором CRM',
)]
final class CreateOrganizationCommand extends Command
{
    public function __construct(
        private readonly OrganizationProvisioner $provisioner,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this
            ->addArgument('name', InputArgument::REQUIRED, 'Название клуба / организации')
            ->addArgument('slug', InputArgument::REQUIRED, 'Slug (fitpower)')
            ->addArgument('admin-email', InputArgument::REQUIRED, 'Email администратора')
            ->addArgument('admin-password', InputArgument::REQUIRED, 'Пароль администратора')
            ->addOption('admin-name', null, InputOption::VALUE_REQUIRED, 'Имя администратора', 'Администратор')
            ->addOption('org-email', null, InputOption::VALUE_REQUIRED, 'Email организации')
            ->addOption('org-phone', null, InputOption::VALUE_REQUIRED, 'Телефон организации')
            ->addOption('demo-days', null, InputOption::VALUE_REQUIRED, 'Дней демо', '14')
            ->addOption('tariff', null, InputOption::VALUE_REQUIRED, 'Тариф', 'demo');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        try {
            $result = $this->provisioner->provision(
                (string) $input->getArgument('name'),
                (string) $input->getArgument('slug'),
                (string) $input->getArgument('admin-email'),
                (string) $input->getArgument('admin-password'),
                (string) $input->getOption('admin-name'),
                $input->getOption('org-email') !== null ? (string) $input->getOption('org-email') : null,
                $input->getOption('org-phone') !== null ? (string) $input->getOption('org-phone') : null,
                (int) $input->getOption('demo-days'),
                (string) $input->getOption('tariff'),
            );
        } catch (\InvalidArgumentException $e) {
            $io->error($e->getMessage());

            return Command::FAILURE;
        }

        $org = $result['organization'];
        $io->success(sprintf(
            'Организация «%s» (slug: %s, id: %d) создана. Админ: %s',
            $org->getName(),
            $org->getSlug(),
            $org->getId(),
            $result['admin']->getEmail(),
        ));

        return Command::SUCCESS;
    }
}
