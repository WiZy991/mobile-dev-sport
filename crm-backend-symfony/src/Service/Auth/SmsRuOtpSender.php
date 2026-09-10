<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Log\LoggerInterface;
use Symfony\Component\DependencyInjection\Attribute\Autowire;
use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * OTP по SMS на номер телефона (sms.ru).
 * Если API-ключ не задан — канал считается не настроенным (в debug код всё равно выдаётся в `dev_code`).
 */
final class SmsRuOtpSender implements OtpSenderInterface
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        #[Autowire('%env(default:sms_ru_api_id_default:SMS_RU_API_ID)%')]
        private readonly string $apiId = '',
        private readonly ?LoggerInterface $logger = null,
    ) {
    }

    public function channel(): string
    {
        return OtpChannel::SMS;
    }

    public function isConfigured(): bool
    {
        return trim($this->apiId) !== '';
    }

    public function send(string $phoneE164, string $code, PhoneOtpChallengeContext $context): OtpDeliveryResult
    {
        if (!$this->isConfigured()) {
            return OtpDeliveryResult::fail('channel_unavailable', 'SMS-провайдер не настроен');
        }

        $to = ltrim($phoneE164, '+');
        $msg = sprintf('Код для входа: %s', $code);

        try {
            $response = $this->httpClient->request('POST', 'https://sms.ru/sms/send', [
                'timeout' => 15,
                'body' => [
                    'api_id' => trim($this->apiId),
                    'to' => $to,
                    'msg' => $msg,
                    'json' => 1,
                ],
            ]);
            $status = $response->getStatusCode();
            $payload = $response->toArray(false);
            $ok = (int) ($payload['status'] ?? 0) === 100
                || (string) ($payload['status'] ?? '') === 'OK';

            // sms.ru: status_code 100 = OK; per-number status in sms.<phone>.status
            if (!$ok && isset($payload['sms']) && \is_array($payload['sms'])) {
                foreach ($payload['sms'] as $row) {
                    if (\is_array($row) && (int) ($row['status_code'] ?? 0) === 100) {
                        $ok = true;
                        break;
                    }
                }
            }

            if ($status >= 200 && $status < 300 && $ok) {
                return OtpDeliveryResult::success(
                    null,
                    'Код отправлен в SMS на ваш номер телефона',
                );
            }

            $this->logger?->warning('SMS.ru OTP failed', [
                'status' => $status,
                'payload' => $payload,
            ]);

            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить SMS с кодом');
        } catch (\Throwable $e) {
            $this->logger?->error('SMS.ru OTP exception', ['e' => $e->getMessage()]);

            return OtpDeliveryResult::fail('channel_failed', 'Не удалось отправить SMS с кодом');
        }
    }
}
