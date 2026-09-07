<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class MaxBotOtpSender implements OtpSenderInterface
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        #[Autowire('%env(default:max_bot_token_default:MAX_BOT_TOKEN)%')]
        private readonly string $botToken = '',
        #[Autowire('%env(default:max_bot_username_default:MAX_BOT_USERNAME)%')]
        private readonly string $botUsername = '',
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function channel(): string
    {
        return OtpChannel::MAX;
    }

    public function isConfigured(): bool
    {
        return trim($this->botToken) !== '';
    }

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult
    {
        if (!$this->isConfigured()) {
            return OtpDeliveryResult::fail('channel_unavailable', 'Бот Max не настроен');
        }

        $username = ltrim(trim($this->botUsername), '@');
        $deeplink = $username !== ''
            ? 'https://max.ru/' . rawurlencode($username) . '?start=' . rawurlencode($context->deeplinkToken)
            : 'https://max.ru';

        return OtpDeliveryResult::success(
            $deeplink,
            'Откройте Max и нажмите «Старт» у бота клуба — код придёт в этот чат.',
        );
    }

    public function deliverCodeToChat(int|string $chatId, string $code): bool
    {
        if (!$this->isConfigured()) {
            return false;
        }
        try {
            $response = $this->httpClient->request('POST', 'https://platform-api.max.ru/messages', [
                'timeout' => 15,
                'query' => ['chat_id' => $chatId],
                'headers' => [
                    'Authorization' => trim($this->botToken),
                    'Content-Type' => 'application/json',
                ],
                'json' => [
                    'text' => 'Код для входа в приложение: ' . $code,
                ],
            ]);

            return $response->getStatusCode() >= 200 && $response->getStatusCode() < 300;
        } catch (\Throwable $e) {
            $this->logger?->error('Max OTP send failed', ['e' => $e->getMessage()]);

            return false;
        }
    }
}
