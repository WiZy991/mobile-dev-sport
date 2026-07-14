<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\SubscriptionPlan;
use App\Service\Admin\SubscriptionPlanDeletionService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:delete-subscription-plan',
    description: 'Удалить тарифный план по названию или ID',
)]
final class DeleteSubscriptionPlanCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly SubscriptionPlanDeletionService $deletionService,
    ) {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this
            ->addArgument('plan', InputArgument::REQUIRED, 'ID или название тарифа')
            ->addOption('force', 'f', InputOption::VALUE_NONE, 'Удалить вместе с выданными абонементами и платежами по этому тарифу');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $planRef = trim((string) $input->getArgument('plan'));
        $force = (bool) $input->getOption('force');

        $repo = $this->em->getRepository(SubscriptionPlan::class);
        $plan = ctype_digit($planRef)
            ? $repo->find((int) $planRef)
            : $repo->findOneBy(['name' => $planRef]);

        if ($plan === null) {
            $io->error('Тариф не найден: ' . $planRef);

            return Command::FAILURE;
        }

        $issued = $this->deletionService->countIssued($plan);
        $payments = $this->deletionService->countPayments($plan);

        if (!$force && !$this->deletionService->canDeleteWithoutForce($plan)) {
            $io->warning(sprintf(
                'Тариф «%s» (id=%d): выдано абонементов — %d, платежей — %d.',
                $plan->getName(),
                $plan->getId(),
                $issued,
                $payments,
            ));
            $io->note('Повторите с --force, чтобы удалить тариф вместе со связанными записями.');

            return Command::FAILURE;
        }

        if ($force && ($issued > 0 || $payments > 0)) {
            $io->warning(sprintf(
                'Принудительное удаление: абонементов — %d, платежей — %d.',
                $issued,
                $payments,
            ));
        }

        $stats = $this->deletionService->delete($plan, $force);
        $io->success(sprintf(
            'Тариф «%s» удалён. Абонементов: %d, продаж: %d, платежей: %d.',
            $planRef,
            $stats['subscriptions'],
            $stats['sales'],
            $stats['payments'],
        ));

        return Command::SUCCESS;
    }
}
