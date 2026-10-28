<?php

declare(strict_types=1);

namespace App\Service\Auth;

use App\Entity\PasswordResetToken;
use App\Entity\StaffUser;
use App\Entity\User;
use App\Service\Mail\CeleryMailPublisher;
use Doctrine\ORM\EntityManagerInterface;
use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * Восстановление пароля: ссылка в письме (сайт / админка).
 * Не раскрывает, есть ли аккаунт с таким email.
 */
final class PasswordResetService
{
    public const GENERIC_OK = 'Если такой email есть в системе, мы отправили письмо со ссылкой. Новый пароль задаёте по ссылке из письма. Проверьте и папку «Спам».';

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CeleryMailPublisher $mailPublisher,
        private readonly UserPasswordHasherInterface $passwordHasher,
        private readonly LoggerInterface $logger,
        #[Autowire('%env(default:mail_public_origin_default:DEFAULT_URI)%')]
        private readonly string $publicSiteOrigin = 'https://worldcashfit.ru',
    ) {
    }

    public function requestReset(string $email, string $audience, string $publicOrigin, ?string $ip = null): void
    {
        $email = mb_strtolower(trim($email));
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return;
        }

        $audience = $audience === PasswordResetToken::AUDIENCE_STAFF
            ? PasswordResetToken::AUDIENCE_STAFF
            : PasswordResetToken::AUDIENCE_CLIENT;

        $found = $audience === PasswordResetToken::AUDIENCE_STAFF
            ? $this->findStaffByEmail($email)
            : $this->findClientByEmail($email);

        if ($found === null) {
            return;
        }

        $this->invalidateOpenTokens($email, $audience);

        $code = str_pad((string) random_int(0, 999999), 6, '0', STR_PAD_LEFT);
        $linkToken = bin2hex(random_bytes(24));

        $row = (new PasswordResetToken())
            ->setAudience($audience)
            ->setUserId((int) $found['id'])
            ->setEmail($email)
            ->setCodeHash($this->hashSecret($code))
            ->setTokenHash($this->hashSecret($linkToken))
            ->setExpiresAt(new \DateTimeImmutable('+30 minutes'))
            ->setIp($ip);
        $this->em->persist($row);
        $this->em->flush();

        $origin = $this->resolvePublicOrigin($publicOrigin);
        $link = $audience === PasswordResetToken::AUDIENCE_STAFF
            ? $origin . '/admin/reset-password?token=' . urlencode($linkToken)
            : $origin . '/forgot-password?token=' . urlencode($linkToken);

        $name = $found['name'] !== '' ? $found['name'] : 'Здравствуйте';
        $subject = 'Восстановление пароля';
        $body = $name . "!\n\n"
            . "Чтобы задать новый пароль, откройте ссылку (действует 30 минут):\n{$link}\n\n"
            . "Если вы не просили восстановить пароль, просто проигнорируйте это письмо.\n";
        $safeName = htmlspecialchars($name, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
        $safeLink = htmlspecialchars($link, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
        $html = '<p>' . $safeName . '!</p>'
            . '<p>Чтобы задать новый пароль, нажмите кнопку. Ссылка действует 30 минут.</p>'
            . '<p><a href="' . $safeLink . '" style="display:inline-block;padding:12px 20px;background:#D35400;color:#fff;text-decoration:none;border-radius:999px;font-weight:700;">Задать новый пароль</a></p>'
            . '<p>Если кнопка не открывается, скопируйте ссылку:<br>' . $safeLink . '</p>'
            . '<p>Если вы не просили восстановить пароль, просто проигнорируйте это письмо.</p>';

        try {
            $this->mailPublisher->enqueuePasswordReset($email, $subject, $body, $html);
        } catch (\Throwable $e) {
            $this->logger->error('Password reset email enqueue failed (redis/celery)', [
                'email' => $email,
                'error' => $e->getMessage(),
            ]);
        }
    }

    public function resetByCode(string $email, string $code, string $newPassword, string $audience): ?string
    {
        $email = mb_strtolower(trim($email));
        $code = preg_replace('/\D+/', '', $code) ?? '';
        if ($email === '' || strlen($code) !== 6) {
            return 'Проверьте email и код из письма.';
        }
        $token = $this->em->createQueryBuilder()
            ->select('t')
            ->from(PasswordResetToken::class, 't')
            ->where('t.email = :email')
            ->andWhere('t.audience = :audience')
            ->andWhere('t.codeHash = :hash')
            ->andWhere('t.usedAt IS NULL')
            ->setParameter('email', $email)
            ->setParameter('audience', $audience)
            ->setParameter('hash', $this->hashSecret($code))
            ->orderBy('t.id', 'DESC')
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return $this->applyToken($token, $newPassword);
    }

    public function resetByLinkToken(string $linkToken, string $newPassword, string $audience): ?string
    {
        $linkToken = trim($linkToken);
        if ($linkToken === '' || strlen($linkToken) < 16) {
            return 'Ссылка недействительна. Запросите восстановление ещё раз.';
        }

        return $this->applyToken($this->findOpenByLinkToken($linkToken, $audience), $newPassword);
    }

    public function validateLinkToken(string $linkToken, string $audience): bool
    {
        $token = $this->findOpenByLinkToken($linkToken, $audience);

        return $token instanceof PasswordResetToken && !$token->isExpired();
    }

    private function findOpenByLinkToken(string $linkToken, string $audience): ?PasswordResetToken
    {
        $linkToken = trim($linkToken);
        if ($linkToken === '') {
            return null;
        }

        return $this->em->createQueryBuilder()
            ->select('t')
            ->from(PasswordResetToken::class, 't')
            ->where('t.tokenHash = :hash')
            ->andWhere('t.audience = :audience')
            ->andWhere('t.usedAt IS NULL')
            ->setParameter('hash', $this->hashSecret($linkToken))
            ->setParameter('audience', $audience)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    private function applyToken(?PasswordResetToken $token, string $newPassword): ?string
    {
        if (!$token instanceof PasswordResetToken || $token->isExpired()) {
            return 'Код или ссылка устарели. Запросите восстановление ещё раз.';
        }
        $newPassword = (string) $newPassword;
        if (mb_strlen($newPassword) < 6) {
            return 'Пароль должен быть не менее 6 символов.';
        }

        if ($token->getAudience() === PasswordResetToken::AUDIENCE_STAFF) {
            $staff = $this->findStaffEntity($token->getUserId());
            if ($staff === null || !$staff->isActive()) {
                return 'Код или ссылка устарели. Запросите восстановление ещё раз.';
            }
            $staff->setPassword($this->passwordHasher->hashPassword($staff, $newPassword));
            $this->em->persist($staff);
        } else {
            $user = $this->withOrganizationFilterDisabled(
                fn () => $this->em->getRepository(User::class)->find($token->getUserId())
            );
            if (!$user instanceof User || $user->isBlocked()) {
                return 'Код или ссылка устарели. Запросите восстановление ещё раз.';
            }
            $user->setPasswordHash(password_hash($newPassword, PASSWORD_BCRYPT));
            $this->em->persist($user);
        }

        $token->markUsed();
        $this->em->persist($token);
        $this->em->flush();

        return null;
    }

    /** @return array{id: int, name: string}|null */
    private function findClientByEmail(string $email): ?array
    {
        $user = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('LOWER(u.email) = :email')
            ->setParameter('email', $email)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
        if (!$user instanceof User || $user->isBlocked()) {
            return null;
        }

        return ['id' => (int) $user->getId(), 'name' => trim($user->getName())];
    }

    /** @return array{id: int, name: string}|null */
    private function findStaffByEmail(string $email): ?array
    {
        $staff = $this->findStaffEntityByEmail($email);
        if ($staff === null || !$staff->isActive()) {
            return null;
        }

        return ['id' => (int) $staff->getId(), 'name' => trim($staff->getName())];
    }

    private function findStaffEntity(int $id): ?StaffUser
    {
        return $this->withOrganizationFilterDisabled(
            fn () => $this->em->getRepository(StaffUser::class)->find($id)
        );
    }

    private function findStaffEntityByEmail(string $email): ?StaffUser
    {
        return $this->withOrganizationFilterDisabled(
            function () use ($email) {
                return $this->em->createQueryBuilder()
                    ->select('s')
                    ->from(StaffUser::class, 's')
                    ->where('LOWER(s.email) = :email')
                    ->setParameter('email', $email)
                    ->setMaxResults(1)
                    ->getQuery()
                    ->getOneOrNullResult();
            }
        );
    }

    /**
     * @template T
     * @param callable(): T $fn
     * @return T
     */
    private function withOrganizationFilterDisabled(callable $fn): mixed
    {
        $filters = $this->em->getFilters();
        $wasEnabled = $filters->isEnabled('organization');
        if ($wasEnabled) {
            $filters->disable('organization');
        }
        try {
            return $fn();
        } finally {
            if ($wasEnabled && !$filters->isEnabled('organization')) {
                $filters->enable('organization');
            }
        }
    }

    private function invalidateOpenTokens(string $email, string $audience): void
    {
        $open = $this->em->createQueryBuilder()
            ->select('t')
            ->from(PasswordResetToken::class, 't')
            ->where('t.email = :email')
            ->andWhere('t.audience = :audience')
            ->andWhere('t.usedAt IS NULL')
            ->setParameter('email', $email)
            ->setParameter('audience', $audience)
            ->getQuery()
            ->getResult();
        $now = new \DateTimeImmutable();
        foreach ($open as $row) {
            $row->setExpiresAt($now);
            $this->em->persist($row);
        }
        if ($open !== []) {
            $this->em->flush();
        }
    }

    private function resolvePublicOrigin(string $fallback): string
    {
        foreach ([$this->publicSiteOrigin, $fallback, 'https://worldcashfit.ru'] as $candidate) {
            $origin = rtrim(trim($candidate), '/');
            if ($origin === '') {
                continue;
            }
            if (!preg_match('#^https?://#i', $origin)) {
                $origin = 'https://' . $origin;
            }
            $host = (string) parse_url($origin, PHP_URL_HOST);
            if ($host === '' || in_array($host, ['localhost', '127.0.0.1', '::1'], true)) {
                continue;
            }

            return $origin;
        }

        return 'https://worldcashfit.ru';
    }

    private function hashSecret(string $value): string
    {
        return hash('sha256', $value);
    }
}
