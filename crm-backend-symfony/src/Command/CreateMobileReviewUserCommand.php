<?php

declare(strict_types=1);

namespace App\Command;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Input\InputOption;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

#[AsCommand(
    name: 'app:create-mobile-review-user',
    description: 'Создать или обновить тестовый аккаунт для App Store Review (email + password)',
)]
final class CreateMobileReviewUserCommand extends Command
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
        parent::__construct();
    }

    protected function configure(): void
    {
        $this
            ->addOption('email', null, InputOption::VALUE_REQUIRED, 'Email для входа', 'appreview@worldcashfit.ru')
            ->addOption('password', null, InputOption::VALUE_REQUIRED, 'Пароль (мин. 6 символов)', 'AppReview2026!')
            ->addOption('name', null, InputOption::VALUE_REQUIRED, 'Имя в профиле', 'App Store Review');
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $email = mb_strtolower(trim((string) $input->getOption('email')));
        $password = (string) $input->getOption('password');
        $name = trim((string) $input->getOption('name'));

        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $io->error('Укажите корректный --email');

            return Command::FAILURE;
        }
        if (mb_strlen($password) < 6) {
            $io->error('Пароль должен быть не короче 6 символов');

            return Command::FAILURE;
        }

        $repo = $this->em->getRepository(User::class);
        $user = $repo->findOneBy(['email' => $email]);
        $created = false;
        if (!$user instanceof User) {
            $user = (new User())
                ->setEmail($email)
                ->setPhone('+7 900 000-00-01')
                ->setBonusPoints(0)
                ->setIsBlocked(false);
            $this->em->persist($user);
            $created = true;
        }

        $user
            ->setName($name !== '' ? $name : 'App Store Review')
            ->setPasswordHash(password_hash($password, PASSWORD_BCRYPT))
            ->setIsBlocked(false);

        $this->em->flush();

        $io->success($created ? 'Создан тестовый пользователь для App Review.' : 'Обновлён тестовый пользователь для App Review.');
        $io->listing([
            'Email: ' . $email,
            'Password: ' . $password,
            'Укажите эти данные в App Store Connect → App Review Information → Sign-in required.',
        ]);

        return Command::SUCCESS;
    }
}
