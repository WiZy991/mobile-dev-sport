<?php

declare(strict_types=1);

namespace App\Service\Api;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;

final class AppleMobileAuthService
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AppleSignInTokenVerifier $tokenVerifier,
    ) {
    }

    /**
     * @return array{sub: string, email?: string}
     */
    public function verifyIdentityToken(string $identityToken): array
    {
        return $this->tokenVerifier->verify($identityToken);
    }

    public function resolveUser(string $appleSub, ?string $email, ?string $fullName): User
    {
        $repo = $this->em->getRepository(User::class);

        $byApple = $repo->findOneBy(['appleId' => $appleSub]);
        if ($byApple instanceof User) {
            if ($byApple->isBlocked()) {
                throw new \RuntimeException('user_blocked');
            }
            $this->maybeUpdateProfile($byApple, $email, $fullName);

            return $byApple;
        }

        if ($email !== null && $email !== '') {
            $byEmail = $repo->findOneBy(['email' => $email]);
            if ($byEmail instanceof User) {
                if ($byEmail->isBlocked()) {
                    throw new \RuntimeException('user_blocked');
                }
                if ($byEmail->getAppleId() === null || $byEmail->getAppleId() === $appleSub) {
                    $byEmail->setAppleId($appleSub);
                    $this->maybeUpdateProfile($byEmail, $email, $fullName);
                    $this->em->flush();

                    return $byEmail;
                }
            }
        }

        return $this->createUser($appleSub, $email, $fullName);
    }

    private function createUser(string $appleSub, ?string $email, ?string $fullName): User
    {
        $resolvedEmail = $this->resolveEmail($appleSub, $email);
        $name = $this->resolveName($fullName);

        $user = (new User())
            ->setEmail($resolvedEmail)
            ->setName($name)
            ->setPhone('+7 900 000-00-00')
            ->setBonusPoints(0)
            ->setIsBlocked(false)
            ->setAppleId($appleSub);

        $this->em->persist($user);
        $this->em->flush();

        return $user;
    }

    private function resolveEmail(string $appleSub, ?string $email): string
    {
        if ($email !== null && $email !== '' && filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $existing = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
            if ($existing === null) {
                return $email;
            }
        }

        return 'apple-' . substr(hash('sha256', $appleSub), 0, 24) . '@users.worldcashfit.ru';
    }

    private function resolveName(?string $fullName): string
    {
        $name = trim((string) $fullName);
        if ($name !== '') {
            return mb_substr($name, 0, 100);
        }

        return 'Пользователь Apple';
    }

    private function maybeUpdateProfile(User $user, ?string $email, ?string $fullName): void
    {
        $dirty = false;
        $name = trim((string) $fullName);
        if ($name !== '' && ($user->getName() === '' || $user->getName() === 'Пользователь Apple')) {
            $user->setName(mb_substr($name, 0, 100));
            $dirty = true;
        }
        if ($email !== null && $email !== '' && str_contains($user->getEmail(), '@users.worldcashfit.ru')) {
            $existing = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
            if ($existing === null) {
                $user->setEmail($email);
                $dirty = true;
            }
        }
        if ($dirty) {
            $this->em->flush();
        }
    }
}
