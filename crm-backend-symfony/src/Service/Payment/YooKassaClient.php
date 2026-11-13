<?php

namespace App\Service\Payment;

use App\Entity\Payment;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * Платежи ЮKassa по одноразовому payment_token из мобильного SDK.
 * https://yookassa.ru/developers/payment-acceptance/integration-scenarios/mobile-sdks/payments-with-tokens
 */
class YooKassaClient
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $shopId,
        private readonly string $secretKey,
        private readonly string $apiUrl = 'https://api.yookassa.ru/v3',
        private readonly bool $sendReceipt = false,
        private readonly int $vatCode = 1,
    ) {}

    public function isConfigured(): bool
    {
        return trim($this->shopId) !== '' && trim($this->secretKey) !== '';
    }

    public function createPaymentWithToken(Payment $payment, string $paymentToken): YooKassaApiResult
    {
        $description = $this->description($payment);
        $amount = [
            'value' => $this->formatAmount($payment->getAmountKopecks()),
            'currency' => 'RUB',
        ];
        $body = [
            'amount' => $amount,
            'capture' => true,
            'payment_token' => $paymentToken,
            'description' => $description,
            'metadata' => [
                'payment_id' => (string) $payment->getId(),
                'order_number' => $payment->getOrderNumber(),
            ],
        ];

        $receipt = $this->buildReceipt($payment, $description, $amount);
        if ($receipt !== null) {
            $body['receipt'] = $receipt;
        }

        return $this->request(
            'POST',
            '/payments',
            $body,
            $payment->getOrderNumber(),
        );
    }

    public function getPayment(string $yookassaPaymentId): YooKassaApiResult
    {
        return $this->request('GET', '/payments/' . rawurlencode($yookassaPaymentId), null, null);
    }

    public function capture(Payment $payment): YooKassaApiResult
    {
        $id = $payment->getYookassaPaymentId();
        if ($id === null || $id === '') {
            return new YooKassaApiResult(ok: false, error: 'Нет идентификатора платежа ЮKassa');
        }

        return $this->request(
            'POST',
            '/payments/' . rawurlencode($id) . '/capture',
            [
                'amount' => [
                    'value' => $this->formatAmount($payment->getAmountKopecks()),
                    'currency' => 'RUB',
                ],
            ],
            $payment->getOrderNumber() . '-capture',
        );
    }

    /**
     * @param array<string, mixed>|null $json
     */
    private function request(string $method, string $path, ?array $json, ?string $idempotenceKey): YooKassaApiResult
    {
        if (!$this->isConfigured()) {
            return new YooKassaApiResult(ok: false, error: 'ЮKassa не настроена', errorCode: 'yookassa_not_configured', httpStatus: 503);
        }

        $options = [
            'auth_basic' => [trim($this->shopId), trim($this->secretKey)],
            'headers' => [
                'Accept' => 'application/json',
            ],
        ];
        if ($idempotenceKey !== null && $idempotenceKey !== '') {
            $options['headers']['Idempotence-Key'] = $idempotenceKey;
        }
        if ($json !== null) {
            $options['json'] = $json;
        }

        try {
            $response = $this->httpClient->request($method, rtrim($this->apiUrl, '/') . $path, $options);
            $status = $response->getStatusCode();
            $data = $response->toArray(false);
        } catch (TransportExceptionInterface $e) {
            return new YooKassaApiResult(ok: false, error: 'ЮKassa недоступна: ' . $e->getMessage(), httpStatus: 502);
        } catch (\Throwable) {
            return new YooKassaApiResult(ok: false, error: 'Некорректный ответ ЮKassa', httpStatus: 502);
        }

        if ($status >= 400 || (($data['type'] ?? '') === 'error')) {
            return new YooKassaApiResult(
                ok: false,
                error: isset($data['description']) ? (string) $data['description'] : 'Ошибка ЮKassa',
                errorCode: isset($data['code']) ? (string) $data['code'] : null,
                httpStatus: $status > 0 ? $status : 502,
            );
        }

        return new YooKassaApiResult(ok: true, payment: $data, httpStatus: $status);
    }

    private function formatAmount(int $kopecks): string
    {
        return number_format($kopecks / 100, 2, '.', '');
    }

    private function description(Payment $payment): string
    {
        $name = $payment->getSubscriptionPlan()?->getName() ?: 'Абонемент';
        $text = 'Абонемент: ' . $name;
        if (mb_strlen($text) > 128) {
            return mb_substr($text, 0, 128);
        }

        return $text;
    }

    /**
     * @param array{value: string, currency: string} $amount
     * @return array<string, mixed>|null
     */
    private function buildReceipt(Payment $payment, string $description, array $amount): ?array
    {
        if (!$this->sendReceipt) {
            return null;
        }

        $user = $payment->getUser();
        $customer = [];
        $email = trim((string) $user?->getEmail());
        if ($email !== '') {
            $customer['email'] = $email;
        }
        $phone = preg_replace('/\D+/', '', (string) $user?->getPhone()) ?? '';
        if ($customer === [] && $phone !== '') {
            $customer['phone'] = $phone;
        }
        if ($customer === []) {
            return null;
        }

        return [
            'customer' => $customer,
            'items' => [[
                'description' => $description,
                'quantity' => '1.00',
                'amount' => $amount,
                'vat_code' => $this->vatCode > 0 ? $this->vatCode : 1,
                'payment_mode' => 'full_payment',
                'payment_subject' => 'service',
            ]],
        ];
    }
}
