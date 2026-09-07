<?php

declare(strict_types=1);

namespace App\Service\Auth;

use App\Entity\User;
use App\Service\Notification\ClientEmailNotifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;

final class EmailVerificationService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly ClientEmailNotifier $emailNotifier,
        #[Autowire('%env(default:sber_id_redirect_uri_default:DEFAULT_URI)%')]
        private readonly string $publicBaseUrl = '',
    ) {
    }

    public function sendConfirmation(User $user): void
    {
        $email = trim($user->getEmail());
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return;
        }
        $token = bin2hex(random_bytes(24));
        $user
            ->setEmailVerifyToken($token)
            ->setEmailVerifyTokenExpiresAt(new \DateTimeImmutable('+2 days'));
        $this->em->flush();

        $base = rtrim($this->publicBaseUrl, '/');
        if ($base === '') {
            $base = 'https://worldcashfit.ru';
        }
        $link = $base . '/verify-email?token=' . urlencode($token);
        $this->emailNotifier->send(
            $email,
            'Подтвердите email',
            "Здравствуйте!\n\nПодтвердите адрес электронной почты, перейдя по ссылке:\n{$link}\n\nСсылка действует 48 часов. Если вы не регистрировались в приложении, просто проигнорируйте письмо.\n",
        );
    }

    public function confirmByToken(string $token): bool
    {
        $token = trim($token);
        if ($token === '') {
            return false;
        }
        $user = $this->em->getRepository(User::class)->findOneBy(['emailVerifyToken' => $token]);
        if (!$user instanceof User) {
            return false;
        }
        $expires = $user->getEmailVerifyTokenExpiresAt();
        if ($expires === null || $expires <= new \DateTimeImmutable()) {
            return false;
        }
        $user
            ->setEmailVerifiedAt(new \DateTimeImmutable())
            ->setEmailVerifyToken(null)
            ->setEmailVerifyTokenExpiresAt(null);
        $this->em->flush();

        return true;
    }
}
