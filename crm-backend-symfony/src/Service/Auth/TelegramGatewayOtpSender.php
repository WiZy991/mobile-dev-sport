<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class TelegramGatewayOtpSender implements OtpSenderInterface
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        #[Autowire('%env(default:telegram_gateway_token_default:TELEGRAM_GATEWAY_TOKEN)%')]
        private readonly string $token = '',
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function channel(): string
    {
        return OtpChannel::TELEGRAM;
    }

    public function isConfigured(): bool
    {
        return trim($this->token) !== '';
    }

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult
    {
        if (!$this->isConfigured()) {
            return OtpDeliveryResult::fail('channel_unavailable', 'Telegram Gateway не настроен');
        }

        $digits = ltrim($phoneE164, '+');
        try {
            $response = $this->httpClient->request('POST', 'https://gatewayapi.telegram.org/sendVerificationMessage', [
                'timeout' => 15,
                'headers' => [
                    'Authorization' => 'Bearer ' . trim($this->token),
                    'Content-Type' => 'application/json',
                ],
                'json' => [
                    'phone_number' => '+' . $digits,
                    'code' => $code,
                    'ttl' => 300,
                ],
            ]);
            $status = $response->getStatusCode();
            $payload = $response->toArray(false);
            $ok = (bool) ($payload['ok'] ?? false);
            if ($status >= 200 && $status < 300 && $ok) {
                return OtpDeliveryResult::success();
            }

            $error = (string) ($payload['error'] ?? $payload['description'] ?? 'telegram_failed');
            $this->logger?->warning('Telegram Gateway OTP failed', ['error' => $error, 'status' => $status]);

            return $this->mapTelegramError($error);
        } catch (\Throwable $e) {
            $this->logger?->error('Telegram Gateway OTP exception', ['e' => $e->getMessage()]);

            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить код в Telegram');
        }
    }

    private function mapTelegramError(string $error): OtpDeliveryResult
    {
        $upper = strtoupper($error);
        if (str_contains($upper, 'ACCESS_TOKEN')) {
            return OtpDeliveryResult::fail(
                'channel_unavailable',
                'Telegram Gateway не настроен. Войдите по почте или Сбер ID.',
            );
        }
        if (str_contains($upper, 'BALANCE')) {
            return OtpDeliveryResult::fail(
                'channel_unavailable',
                'На балансе Telegram Gateway нет средств для отправки кода.',
            );
        }
        if (
            str_contains($upper, 'PHONE_NUMBER_NOT_USED')
            || str_contains($upper, 'PHONE_NUMBER_INVALID')
            || str_contains($upper, 'NOT_AVAILABLE')
            || str_contains($upper, 'ABILITY')
            || str_contains($upper, 'PHONE')
        ) {
            return OtpDeliveryResult::fail(
                'channel_undeliverable',
                'Этот номер не принимает коды в Telegram. Выберите Max или WhatsApp.',
            );
        }

        return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить код в Telegram');
    }
}
