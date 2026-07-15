<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputArgument;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:user:login-diagnose',
    description: 'Проверка, какую подсказку входа получит email (Сбер ID / пароль)',
)]
final class DiagnoseUserLoginCommand extends Command
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this->addArgument('email', InputArgument::REQUIRED, 'Email пользователя');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = mb_strtolower(trim((string) $input->getArgument('email')));

        $user = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('LOWER(u.email) = :email')
            ->setParameter('email', $email)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        if (!$user instanceof User) {
            $io->warning("Пользователь с email «{$email}» не найден в БД.");
            $io->text('Приложение покажет: «Введите пароль» (аккаунт неизвестен).');

            return Command::SUCCESS;
        }

        $hash = $user->getPasswordHash();
        $hashEmpty = $hash === null || trim((string) $hash) === '';
        $sberId = $user->getSberId();
        $sberLinked = ($sberId !== null && trim($sberId) !== '')
            || $user->getPassportVerificationProvider() === 'sber_id';

        $io->title('Диагностика входа: ' . $email);
        $io->table(
            ['Поле', 'Значение'],
            [
                ['id', (string) $user->getId()],
                ['email в БД', $user->getEmail()],
                ['sber_id', $sberId ?: '—'],
                ['passport_verification_provider', $user->getPassportVerificationProvider() ?: '—'],
                ['password_hash пустой', $hashEmpty ? 'да' : 'нет'],
                ['признак Сбер ID', $sberLinked ? 'да' : 'нет'],
            ],
        );

        if ($hashEmpty) {
            $hint = $sberLinked
                ? 'password_not_set — подсказка про вход через Сбер ID'
                : 'password_not_set — пароль не задан (не Сбер)';
            $io->success('При пустом пароле API вернёт: ' . $hint);
        } else {
            $io->warning('При пустом пароле API вернёт: password_required — «Введите пароль».');
            if ($sberLinked) {
                $io->note(
                    'У аккаунта Сбер ID в БД уже записан password_hash. '
                    . 'Если пароль не задавался вручную — проверьте, не слился ли аккаунт с обычной регистрацией.'
                );
            }
        }

        return Command::SUCCESS;
    }
}
