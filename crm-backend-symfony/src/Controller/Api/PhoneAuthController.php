<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\StaffNotification;
use App\Entity\User;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\Auth\EmailVerificationService;
use App\Service\Auth\PhoneNormalizer;
use App\Service\Auth\PhoneOtpService;
use App\Service\Lead\LeadIngestionService;
use App\Service\Lead\LeadSource;
use App\Service\MobileClientPayloadApplier;
use App\Service\Staff\StaffEventNotifier;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/auth')]
class PhoneAuthController extends AbstractController
{
    public function __construct(
        private readonly PhoneOtpService $otp,
        private readonly EntityManagerInterface $em,
        private readonly MobileAuthTokenIssuer $mobileTokens,
        private readonly MobileClientPayloadApplier $payloadApplier,
        private readonly LeadIngestionService $leadIngestion,
        private readonly StaffEventNotifier $staffEventNotifier,
        private readonly EmailVerificationService $emailVerification,
    ) {
    }

    #[Route('/otp/channels', name: 'api_auth_otp_channels', methods: ['GET'])]
    public function channels(): JsonResponse
    {
        return $this->json(['channels' => $this->otp->channelStatuses()]);
    }

    #[Route('/otp/request', name: 'api_auth_otp_request', methods: ['POST'])]
    public function requestOtp(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        try {
            $payload = $this->otp->request(
                (string) ($data['phone'] ?? ''),
                strtolower(trim((string) ($data['channel'] ?? ''))),
            );
        } catch (\DomainException $e) {
            return $this->otpError($e->getMessage());
        }

        return $this->json($payload);
    }

    #[Route('/otp/verify', name: 'api_auth_otp_verify', methods: ['POST'])]
    public function verifyOtp(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        try {
            $result = $this->otp->verify(
                (string) ($data['phone'] ?? ''),
                (string) ($data['code'] ?? ''),
            );
        } catch (\DomainException $e) {
            return $this->otpError($e->getMessage());
        }

        if (isset($result['user']) && $result['user'] instanceof User) {
            return $this->json($this->mobileTokens->issue($result['user'], true));
        }

        return $this->json([
            'registration_required' => true,
            'otp_ticket' => $result['otp_ticket'],
            'phone' => $result['phone'],
        ]);
    }

    #[Route('/otp/max-webhook', name: 'api_auth_otp_max_webhook', methods: ['POST'])]
    public function maxWebhook(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        if (!\is_array($data)) {
            $data = $request->request->all();
        }
        $this->otp->handleMaxWebhook(\is_array($data) ? $data : []);

        return $this->json(['ok' => true]);
    }

    #[Route('/register/check-email', name: 'api_auth_register_check_email', methods: ['POST'])]
    public function checkEmail(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Некорректный email', 'code' => 'invalid_email'], 400);
        }

        $existing = $this->findUserByEmail($email);
        if ($existing === null) {
            return $this->json(['exists' => false]);
        }

        return $this->json([
            'exists' => true,
            'masked_phone' => PhoneNormalizer::mask($existing->getPhone()),
            'message' => 'Аккаунт с этой почтой уже есть. Обратитесь в поддержку, чтобы восстановить доступ и сменить телефон.',
        ]);
    }

    #[Route('/register/phone', name: 'api_auth_register_phone', methods: ['POST'])]
    public function registerPhone(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $ticket = trim((string) ($data['otp_ticket'] ?? ''));
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $name = trim((string) ($data['name'] ?? ''));

        if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Некорректный email', 'code' => 'invalid_email'], 400);
        }
        if ($name === '') {
            return $this->json(['error' => 'Укажите фамилию и имя', 'code' => 'missing_name'], 400);
        }
        if ($this->findUserByEmail($email) !== null) {
            return $this->json([
                'error' => 'Аккаунт с этой почтой уже есть',
                'code' => 'email_taken',
            ], 409);
        }

        try {
            $challenge = $this->otp->consumeRegistrationTicket($ticket);
        } catch (\DomainException $e) {
            return $this->otpError($e->getMessage());
        }

        $phone = $challenge->getPhone();
        if ($this->otp->findUserByPhone($phone) !== null) {
            return $this->json(['error' => 'Этот номер уже зарегистрирован', 'code' => 'phone_taken'], 409);
        }

        $user = (new User())
            ->setEmail($email)
            ->setName(mb_substr($name, 0, 100))
            ->setPhone($phone)
            ->setBonusPoints(0)
            ->setIsBlocked(false)
            ->setPasswordHash(null);

        $this->payloadApplier->applyRegistrationPayload($user, $data);
        $this->em->persist($user);
        $this->em->flush();

        $referralCode = trim((string) ($data['referral_code'] ?? $data['promo_code'] ?? ''));
        if ($referralCode !== '') {
            try {
                $this->leadIngestion->ingest(
                    $name,
                    $phone,
                    $email,
                    LeadSource::REFERRAL,
                    'Регистрация по рекомендации. Код: ' . $referralCode,
                    $user,
                );
                $this->em->flush();
            } catch (\InvalidArgumentException) {
            }
        } else {
            $this->leadIngestion->attachUserIfOpenLead($phone, $user, 'Клиент зарегистрировался в приложении');
            $this->em->flush();
        }

        $this->staffEventNotifier->notifyBySection(
            'clients',
            StaffNotification::TYPE_CLIENT,
            'Новый клиент в приложении',
            sprintf('%s (%s) зарегистрировался', $user->getName(), $user->getEmail()),
            $user->getId() !== null ? (string) $user->getId() : null,
        );

        try {
            $this->emailVerification->sendConfirmation($user);
        } catch (\Throwable) {
        }

        return $this->json($this->mobileTokens->issue($user, true));
    }

    private function findUserByEmail(string $email): ?User
    {
        $normalized = mb_strtolower(trim($email));
        if ($normalized === '') {
            return null;
        }

        return $this->em->createQueryBuilder()
            ->select('u')
            ->from(User::class, 'u')
            ->where('LOWER(u.email) = :email')
            ->setParameter('email', $normalized)
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();
    }

    private function otpError(string $code): JsonResponse
    {
        $map = [
            'invalid_phone' => [400, 'Укажите номер телефона полностью'],
            'invalid_channel' => [400, 'Выберите Telegram, Max или WhatsApp'],
            'channel_unavailable' => [503, 'Этот канал сейчас недоступен. Выберите другой.'],
            'channel_undeliverable' => [422, 'Не удалось доставить код в этот мессенджер. Выберите другой канал.'],
            'channel_failed' => [502, 'Не удалось отправить код. Попробуйте другой канал.'],
            'otp_rate_limited' => [429, 'Слишком много запросов кода. Подождите час.'],
            'otp_too_soon' => [429, 'Повторная отправка будет доступна через несколько секунд'],
            'otp_not_found' => [400, 'Сначала запросите код'],
            'otp_expired' => [400, 'Код устарел, запросите новый'],
            'otp_locked' => [429, 'Слишком много попыток. Запросите новый код'],
            'invalid_code' => [400, 'Неверный код'],
            'invalid_ticket' => [400, 'Сессия регистрации устарела, подтвердите номер снова'],
            'ticket_expired' => [400, 'Сессия регистрации истекла, подтвердите номер снова'],
            'user_blocked' => [403, 'Access denied'],
        ];
        [$status, $message] = $map[$code] ?? [400, 'Не удалось обработать код'];

        return $this->json(['error' => $message, 'code' => $code], $status);
    }
}
