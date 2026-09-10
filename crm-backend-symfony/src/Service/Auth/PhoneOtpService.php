<?php

declare(strict_types=1);

namespace App\Service\Auth;

use App\Entity\PhoneOtpChallenge;
use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Component\DependencyInjection\Attribute\AutowireIterator;

final class PhoneOtpService
{
    private const TTL_SECONDS = 300;
    private const MAX_ATTEMPTS = 5;
    private const RESEND_SECONDS = 20;
    private const HOUR_LIMIT = 5;
    private const TICKET_TTL_SECONDS = 1800;

    /** @var array<string, OtpSenderInterface> */
    private array $senders = [];

    /**
     * @param iterable<OtpSenderInterface> $senders
     */
    public function __construct(
        private readonly EntityManagerInterface $em,
        #[AutowireIterator('app.otp_sender')]
        iterable $senders,
        #[Autowire('%kernel.debug%')]
        private readonly bool $debug = false,
    ) {
        foreach ($senders as $sender) {
            $this->senders[$sender->channel()] = $sender;
        }
    }

    /** @return list<array{id: string, available: bool}> */
    public function channelStatuses(): array
    {
        $out = [];
        foreach (OtpChannel::all() as $id) {
            $sender = $this->senders[$id] ?? null;
            $out[] = [
                'id' => $id,
                'available' => $this->debug || ($sender instanceof OtpSenderInterface && $sender->isConfigured()),
            ];
        }

        return $out;
    }

    /**
     * @return array<string, mixed>
     */
    public function request(string $rawPhone, string $channel): array
    {
        $phone = PhoneNormalizer::toE164($rawPhone);
        if ($phone === null) {
            throw new \DomainException('invalid_phone');
        }
        $channel = strtolower(trim($channel));
        if ($channel === '' || !OtpChannel::isValid($channel)) {
            // Старые клиенты могли слать telegram/max/whatsapp — переводим на SMS.
            $channel = OtpChannel::SMS;
        }

        $sender = $this->senders[$channel] ?? null;
        $configured = $sender instanceof OtpSenderInterface && $sender->isConfigured();
        if (!$configured && !$this->debug) {
            throw new \DomainException('channel_unavailable');
        }

        $this->assertRateLimit($phone);

        $code = (string) random_int(100000, 999999);
        $challenge = new PhoneOtpChallenge();
        $challenge
            ->setPhone($phone)
            ->setChannel($channel)
            ->setCodeHash(password_hash($code, PASSWORD_BCRYPT))
            ->setExpiresAt(new \DateTimeImmutable('+' . self::TTL_SECONDS . ' seconds'))
            ->setDeeplinkToken(bin2hex(random_bytes(12)));

        $delivery = OtpDeliveryResult::success(
            null,
            'Код отправлен в SMS на ваш номер телефона',
        );
        if ($configured && $sender instanceof OtpSenderInterface) {
            $delivery = $sender->send($phone, $code, new PhoneOtpChallengeContext(
                (string) $challenge->getDeeplinkToken(),
            ));
            if (!$delivery->ok) {
                throw new \DomainException($delivery->errorCode ?? 'channel_failed');
            }
            $challenge->setSentAt(new \DateTimeImmutable());
        }

        $this->em->persist($challenge);
        $this->em->flush();

        $payload = [
            'ok' => true,
            'channel' => $channel,
            'resend_after_sec' => self::RESEND_SECONDS,
            'ttl_sec' => self::TTL_SECONDS,
            'instruction' => $delivery->instruction
                ?? 'Код отправлен в SMS на ваш номер телефона',
        ];
        if ($delivery->deeplink !== null) {
            $payload['deeplink'] = $delivery->deeplink;
        }
        if ($this->debug) {
            $payload['dev_code'] = $code;
        }

        return $payload;
    }

    /**
     * @return array{user: User}|array{registration_required: true, otp_ticket: string, phone: string}
     */
    public function verify(string $rawPhone, string $code): array
    {
        $phone = PhoneNormalizer::toE164($rawPhone);
        if ($phone === null) {
            throw new \DomainException('invalid_phone');
        }
        $code = preg_replace('/\D+/', '', $code) ?? '';
        if (strlen($code) !== 6) {
            throw new \DomainException('invalid_code');
        }

        $challenge = $this->latestOpenChallenge($phone);
        if ($challenge === null) {
            throw new \DomainException('otp_not_found');
        }
        if ($challenge->isExpired()) {
            throw new \DomainException('otp_expired');
        }
        if ($challenge->getAttempts() >= self::MAX_ATTEMPTS) {
            throw new \DomainException('otp_locked');
        }

        $challenge->incrementAttempts();
        $this->em->flush();

        if (!password_verify($code, $challenge->getCodeHash())) {
            throw new \DomainException('invalid_code');
        }

        $user = $this->findUserByPhone($phone);
        if ($user instanceof User) {
            if ($user->isBlocked()) {
                throw new \DomainException('user_blocked');
            }
            $challenge->setConsumedAt(new \DateTimeImmutable());
            $challenge->setDeliveryCode(null);
            $this->em->flush();

            return ['user' => $user];
        }

        $ticket = bin2hex(random_bytes(24));
        $challenge
            ->setRegistrationTicket($ticket)
            ->setRegistrationTicketExpiresAt(new \DateTimeImmutable('+' . self::TICKET_TTL_SECONDS . ' seconds'))
            ->setConsumedAt(new \DateTimeImmutable())
            ->setDeliveryCode(null);
        $this->em->flush();

        return [
            'registration_required' => true,
            'otp_ticket' => $ticket,
            'phone' => $phone,
        ];
    }

    public function consumeRegistrationTicket(string $ticket): PhoneOtpChallenge
    {
        $ticket = trim($ticket);
        if ($ticket === '') {
            throw new \DomainException('invalid_ticket');
        }
        $challenge = $this->em->getRepository(PhoneOtpChallenge::class)->findOneBy([
            'registrationTicket' => $ticket,
        ]);
        if (!$challenge instanceof PhoneOtpChallenge) {
            throw new \DomainException('invalid_ticket');
        }
        $expires = $challenge->getRegistrationTicketExpiresAt();
        if ($expires === null || $expires <= new \DateTimeImmutable()) {
            throw new \DomainException('ticket_expired');
        }
        $challenge->setRegistrationTicket(null);
        $challenge->setRegistrationTicketExpiresAt(null);
        $this->em->flush();

        return $challenge;
    }

    public function handleMaxWebhook(array $payload): void
    {
        $text = $this->extractMaxStartText($payload);
        $chatId = $this->extractMaxChatId($payload);
        if ($text === null || $chatId === null) {
            return;
        }
        $token = $this->extractStartToken($text);
        if ($token === null) {
            return;
        }
        $challenge = $this->em->getRepository(PhoneOtpChallenge::class)->findOneBy([
            'deeplinkToken' => $token,
        ]);
        if (!$challenge instanceof PhoneOtpChallenge || $challenge->isExpired()) {
            return;
        }
        $code = $challenge->getDeliveryCode();
        if ($code === null || $code === '') {
            return;
        }
        $sender = $this->senders[OtpChannel::MAX] ?? null;
        if ($sender instanceof MaxBotOtpSender && $sender->deliverCodeToChat($chatId, $code)) {
            $challenge->setDeliveryCode(null);
            $challenge->setSentAt(new \DateTimeImmutable());
            $this->em->flush();
        }
    }

    public function findUserByPhone(string $phoneE164): ?User
    {
        $national = PhoneNormalizer::national10($phoneE164);
        if (strlen($national) !== 10) {
            return null;
        }

        /** @var list<User> $candidates */
        $candidates = $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('u.phone LIKE :tail')
            ->andWhere('u.isBlocked = false')
            ->setParameter('tail', '%' . $national)
            ->orderBy('u.id', 'DESC')
            ->setMaxResults(20)
            ->getQuery()
            ->getResult();

        foreach ($candidates as $user) {
            if (PhoneNormalizer::national10($user->getPhone()) === $national) {
                return $user;
            }
        }

        return null;
    }

    private function latestOpenChallenge(string $phone): ?PhoneOtpChallenge
    {
        /** @var PhoneOtpChallenge|null $row */
        $row = $this->em->createQueryBuilder()
            ->select('c')
            ->from(PhoneOtpChallenge::class, 'c')
            ->where('c.phone = :phone')
            ->andWhere('c.consumedAt IS NULL')
            ->orderBy('c.id', 'DESC')
            ->setParameter('phone', $phone)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return $row;
    }

    private function assertRateLimit(string $phone): void
    {
        $sinceHour = (new \DateTimeImmutable('-1 hour'))->format('Y-m-d H:i:s');
        $countHour = (int) $this->em->createQueryBuilder()
            ->select('COUNT(c.id)')
            ->from(PhoneOtpChallenge::class, 'c')
            ->where('c.phone = :phone')
            ->andWhere('c.createdAt >= :since')
            ->setParameter('phone', $phone)
            ->setParameter('since', $sinceHour)
            ->getQuery()
            ->getSingleScalarResult();
        if ($countHour >= self::HOUR_LIMIT) {
            throw new \DomainException('otp_rate_limited');
        }

        $latest = $this->em->createQueryBuilder()
            ->select('c')
            ->from(PhoneOtpChallenge::class, 'c')
            ->where('c.phone = :phone')
            ->orderBy('c.id', 'DESC')
            ->setParameter('phone', $phone)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
        if ($latest instanceof PhoneOtpChallenge) {
            $elapsed = time() - $latest->getCreatedAt()->getTimestamp();
            if ($elapsed < self::RESEND_SECONDS) {
                throw new \DomainException('otp_too_soon');
            }
        }
    }

    private function extractMaxStartText(array $payload): ?string
    {
        $candidates = [
            $payload['message']['body']['text'] ?? null,
            $payload['message']['text'] ?? null,
            $payload['update']['message']['body']['text'] ?? null,
        ];
        foreach ($candidates as $text) {
            if (\is_string($text) && trim($text) !== '') {
                return trim($text);
            }
        }

        return null;
    }

    private function extractMaxChatId(array $payload): int|string|null
    {
        $candidates = [
            $payload['message']['recipient']['chat_id'] ?? null,
            $payload['message']['chat_id'] ?? null,
            $payload['chat_id'] ?? null,
        ];
        foreach ($candidates as $id) {
            if ($id !== null && $id !== '') {
                return $id;
            }
        }

        return null;
    }

    private function extractStartToken(string $text): ?string
    {
        if (preg_match('/start(?:\s|=)([a-f0-9]{16,64})/i', $text, $m)) {
            return strtolower($m[1]);
        }
        if (preg_match('/^\/start\s+([a-f0-9]{16,64})/i', $text, $m)) {
            return strtolower($m[1]);
        }
        if (preg_match('/^[a-f0-9]{16,64}$/i', $text)) {
            return strtolower($text);
        }

        return null;
    }
}
