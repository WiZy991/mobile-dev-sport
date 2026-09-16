<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * OTP по SMS через SMS Центр (smsc.ru).
 * Нужны SMSC_LOGIN и SMSC_PASSWORD в .env.
 */
final class SmscOtpSender implements OtpSenderInterface
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly LoggerInterface $logger,
        #[Autowire('%env(default:smsc_login_default:SMSC_LOGIN)%')]
        private readonly string $login = '',
        #[Autowire('%env(default:smsc_password_default:SMSC_PASSWORD)%')]
        private readonly string $password = '',
    ) {
    }

    public function channel(): string
    {
        return OtpChannel::SMS;
    }

    public function isConfigured(): bool
    {
        return trim($this->login) !== '' && trim($this->password) !== '';
    }

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult
    {
        if (!$this->isConfigured()) {
            return OtpDeliveryResult::fail('channel_unavailable', 'SMS-провайдер не настроен');
        }

        $to = ltrim($phoneE164, '+');
        $msg = sprintf('Код для входа: %s', $code);

        try {
            // https://smsc.ru/api/ — HTTP API send.php, fmt=3 (JSON)
            $response = $this->httpClient->request('POST', 'https://smsc.ru/sys/send.php', [
                'timeout' => 15,
                'body' => [
                    'login' => trim($this->login),
                    'psw' => trim($this->password),
                    'phones' => $to,
                    'mes' => $msg,
                    'charset' => 'utf-8',
                    'fmt' => 3,
                ],
            ]);
            $status = $response->getStatusCode();
            $payload = $response->toArray(false);

            $errorCode = isset($payload['error_code']) ? (int) $payload['error_code'] : 0;
            $ok = $status >= 200 && $status < 300
                && $errorCode === 0
                && isset($payload['id']);

            if ($ok) {
                return OtpDeliveryResult::success(
                    null,
                    'Код отправлен в SMS на ваш номер телефона',
                );
            }

            $this->logger->warning('SMSC.ru OTP failed', [
                'http_status' => $status,
                'error_code' => $errorCode,
                'error' => $payload['error'] ?? null,
                'payload' => $payload,
                'phone' => $to,
            ]);

            // 3 = нет денег; 1/2 = логин/пароль; 4 = IP запрещён в кабинете smsc
            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить SMS с кодом');
        } catch (\Throwable $e) {
            $this->logger->error('SMSC.ru OTP exception', ['e' => $e->getMessage()]);

            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить SMS с кодом');
        }
    }
}
