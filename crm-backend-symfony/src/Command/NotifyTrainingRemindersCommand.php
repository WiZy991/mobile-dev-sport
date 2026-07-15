<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\Booking;
use App\Entity\Notification;
use App\Entity\Training;
use App\Entity\User;
use App\Service\Notification\ClientNotificationService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Напоминания о тренировках за ~1 час до начала.
 * Cron (каждые 15 мин): * /15 * * * * php bin/console app:notify-training-reminders
 */
#[AsCommand(name: 'app:notify-training-reminders', description: 'Push/email напоминания о тренировках за 1 час')]
final class NotifyTrainingRemindersCommand extends Command
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientNotificationService $clientNotifications,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $now = new \DateTimeImmutable();
        $from = $now->modify('+50 minutes');
        $to = $now->modify('+70 minutes');

        /** @var list<Training> $trainings */
        $trainings = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Training::class, 't')
            ->where('t.startAt >= :from')
            ->andWhere('t.startAt <= :to')
            ->setParameter('from', $from)
            ->setParameter('to', $to)
            ->getQuery()
            ->getResult();

        $sent = 0;
        foreach ($trainings as $training) {
            $bookings = $this->em->getRepository(Booking::class)->findBy([
                'training' => $training,
                'status' => 'confirmed',
            ]);

            foreach ($bookings as $booking) {
                if (!$booking instanceof Booking) {
                    continue;
                }
                $user = $booking->getUser();
                if (!$user instanceof User || !$user->isNotifyTrainingReminders()) {
                    continue;
                }

                $ref = 'reminder-' . $training->getId() . '-' . $user->getId() . '-' . $training->getStartAt()->format('YmdHi');
                $this->clientNotifications->notify(
                    $user,
                    Notification::TYPE_TRAINING_REMINDER,
                    'Напоминание о тренировке',
                    sprintf(
                        'Через час начинается «%s» (%s).',
                        $training->getName(),
                        $training->getStartAt()->format('H:i')
                    ),
                    $ref,
                );
                ++$sent;
            }
        }

        $io->success(sprintf('Отправлено напоминаний: %d (тренировок в окне: %d)', $sent, count($trainings)));

        return Command::SUCCESS;
    }
}
