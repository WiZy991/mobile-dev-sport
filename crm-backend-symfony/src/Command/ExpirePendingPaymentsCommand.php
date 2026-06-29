<?php

namespace App\Command;

use App\Entity\Payment;
use App\Service\Payment\PaymentStatusSyncService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;

#[AsCommand(
    name: 'app:payments:expire-pending',
    description: 'Expire stale pending payments and optionally sync status from Alfa gateway',
)]
class ExpirePendingPaymentsCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly PaymentStatusSyncService $statusSyncService,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $now = new \DateTimeImmutable();
        $payments = $this->em->getRepository(Payment::class)->findBy(['status' => Payment::STATUS_PENDING]);

        $expired = 0;
        $synced = 0;

        foreach ($payments as $payment) {
            if ($payment->getAlfaOrderId() !== null) {
                $this->statusSyncService->syncFromGateway($payment);
                $this->em->refresh($payment);
                if (!$payment->isPending()) {
                    $synced++;
                    continue;
                }
            }

            if ($payment->getExpiresAt() !== null && $payment->getExpiresAt() < $now) {
                $payment->setStatus(Payment::STATUS_EXPIRED);
                $expired++;
            }
        }

        $this->em->flush();

        $output->writeln(sprintf('Synced: %d, expired: %d', $synced, $expired));

        return Command::SUCCESS;
    }
}
