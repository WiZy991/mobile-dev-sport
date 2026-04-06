<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\Notification;
use App\Entity\Subscription;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Создаёт in-app уведомления за 5–7 дней до окончания абонемента (ТЗ).
 * Запуск: php bin/console app:notify-subscription-expiring
 * В cron: ежедневно 0 9 * * *
 */
#[AsCommand(name: 'app:notify-subscription-expiring', description: 'Уведомления об окончании абонемента (за 5–7 дней)')]
final class NotifySubscriptionExpiringCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $today = new \DateTimeImmutable('today');
        $from = $today->modify('+5 days');
        $to = $today->modify('+7 days');

        $qb = $this->em->createQueryBuilder()
            ->select('s')
            ->from(Subscription::class, 's')
            ->where('s.status = :status')
            ->andWhere('s.endDate IS NOT NULL')
            ->andWhere('s.endDate >= :from')
            ->andWhere('s.endDate <= :to')
            ->setParameter('status', 'active')
            ->setParameter('from', $from)
            ->setParameter('to', $to);

        /** @var Subscription[] $subs */
        $subs = $qb->getQuery()->getResult();
        $created = 0;

        foreach ($subs as $sub) {
            $end = $sub->getEndDate();
            if ($end === null) {
                continue;
            }
            $ref = 'expiry-' . $sub->getId() . '-' . $end->format('Y-m-d');
            $exists = $this->em->getRepository(Notification::class)->findOneBy(['referenceId' => $ref]);
            if ($exists !== null) {
                continue;
            }

            $daysLeft = (int) $today->diff($end)->format('%a');
            $n = (new Notification())
                ->setUser($sub->getUser())
                ->setType(Notification::TYPE_SUBSCRIPTION)
                ->setTitle('Абонемент скоро закончится')
                ->setBody(sprintf(
                    'До окончания абонемента «%s» осталось %d дн. Оформите продление в приложении.',
                    $sub->getPlan()->getName(),
                    $daysLeft
                ))
                ->setReferenceId($ref);

            $this->em->persist($n);
            ++$created;
        }

        $this->em->flush();
        $io->success(sprintf('Создано уведомлений: %d (проверено абонементов в окне 5–7 дней: %d)', $created, count($subs)));

        return Command::SUCCESS;
    }
}
