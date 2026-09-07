<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\HttpClient\HttpClientInterface;

final class GreenApiWhatsAppOtpSender implements OtpSenderInterface
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        #[Autowire('%env(default:green_api_id_default:GREEN_API_ID_INSTANCE)%')]
        private readonly string $idInstance = '',
        #[Autowire('%env(default:green_api_token_default:GREEN_API_TOKEN)%')]
        private readonly string $apiToken = '',
        #[Autowire('%env(default:green_api_url_default:GREEN_API_URL)%')]
        private readonly string $apiUrl = 'https://api.green-api.com',
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function channel(): string
    {
        return OtpChannel::WHATSAPP;
    }

    public function isConfigured(): bool
    {
        return trim($this->idInstance) !== '' && trim($this->apiToken) !== '';
    }

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult
    {
        if (!$this->isConfigured()) {
            return OtpDeliveryResult::fail('channel_unavailable', 'WhatsApp (Green-API) не настроен');
        }

        $digits = ltrim($phoneE164, '+');
        $url = rtrim($this->apiUrl, '/') . '/waInstance' . trim($this->idInstance)
            . '/sendMessage/' . trim($this->apiToken);

        try {
            $response = $this->httpClient->request('POST', $url, [
                'timeout' => 20,
                'headers' => ['Content-Type' => 'application/json'],
                'json' => [
                    'chatId' => $digits . '@c.us',
                    'message' => 'Код для входа в приложение: ' . $code,
                ],
            ]);
            $status = $response->getStatusCode();
            if ($status >= 200 && $status < 300) {
                return OtpDeliveryResult::success();
            }

            $this->logger?->warning('Green-API OTP failed', ['status' => $status, 'body' => $response->getContent(false)]);

            return OtpDeliveryResult::fail(
                'channel_undeliverable',
                'Не удалось отправить код в WhatsApp. Выберите другой канал.',
            );
        } catch (\Throwable $e) {
            $this->logger?->error('Green-API OTP exception', ['e' => $e->getMessage()]);

            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить код в WhatsApp');
        }
    }
}
