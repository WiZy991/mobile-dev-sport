<?php

namespace App\Service\Payment;

use Symfony\Contracts\HttpClient\HttpClientInterface;

class AlfaAcquiringClient
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $apiUrl,
        private readonly string $username,
        private readonly string $password,
    ) {}

    public function registerOrder(AlfaRegisterOrderRequest $request): AlfaRegisterOrderResponse
    {
        $params = [
            'userName' => $this->username,
            'password' => $this->password,
            'orderNumber' => $request->orderNumber,
            'amount' => (string) $request->amountKopecks,
            'currency' => '643',
            'returnUrl' => $request->returnUrl,
            'failUrl' => $request->failUrl,
            'description' => $request->description,
            'pageView' => 'MOBILE',
            'sessionTimeoutSecs' => (string) $request->sessionTimeoutSecs,
        ];

        if ($request->dynamicCallbackUrl !== null && $request->dynamicCallbackUrl !== '') {
            $params['dynamicCallbackUrl'] = $request->dynamicCallbackUrl;
        }

        $jsonParams = [];
        if ($request->email !== null && $request->email !== '') {
            $jsonParams['email'] = $request->email;
        }
        if ($request->phone !== null && $request->phone !== '') {
            $jsonParams['phone'] = $request->phone;
        }
        if ($jsonParams !== []) {
            $params['jsonParams'] = json_encode($jsonParams, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        }

        if ($request->orderBundle !== null) {
            $params['orderBundle'] = json_encode($request->orderBundle, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
        }

        $data = $this->post('register.do', $params);

        return new AlfaRegisterOrderResponse(
            orderId: isset($data['orderId']) ? (string) $data['orderId'] : null,
            formUrl: isset($data['formUrl']) ? (string) $data['formUrl'] : null,
            errorCode: isset($data['errorCode']) ? (int) $data['errorCode'] : null,
            errorMessage: isset($data['errorMessage']) ? (string) $data['errorMessage'] : null,
        );
    }

    public function getOrderStatusExtended(string $orderId): AlfaOrderStatus
    {
        $data = $this->post('getOrderStatusExtended.do', [
            'userName' => $this->username,
            'password' => $this->password,
            'orderId' => $orderId,
        ]);

        $orderStatus = isset($data['orderStatus']) ? (int) $data['orderStatus'] : 0;
        $paymentState = null;
        $paymentWay = null;
        $amount = isset($data['amount']) ? (int) $data['amount'] : null;

        if (isset($data['paymentAmountInfo']) && is_array($data['paymentAmountInfo'])) {
            $paymentState = isset($data['paymentAmountInfo']['paymentState'])
                ? (string) $data['paymentAmountInfo']['paymentState']
                : null;
            if (isset($data['paymentAmountInfo']['approvedAmount'])) {
                $amount = (int) $data['paymentAmountInfo']['approvedAmount'];
            }
        }

        if (isset($data['cardAuthInfo']['paymentWay'])) {
            $paymentWay = (string) $data['cardAuthInfo']['paymentWay'];
        } elseif (isset($data['paymentWay'])) {
            $paymentWay = (string) $data['paymentWay'];
        }

        return new AlfaOrderStatus(
            orderStatus: $orderStatus,
            paymentState: $paymentState,
            amountKopecks: $amount,
            paymentWay: $paymentWay,
            errorCode: isset($data['errorCode']) ? (int) $data['errorCode'] : null,
            errorMessage: isset($data['errorMessage']) ? (string) $data['errorMessage'] : null,
            raw: $data,
        );
    }

    /**
     * @param array<string, string> $params
     * @return array<string, mixed>
     */
    private function post(string $method, array $params): array
    {
        $url = rtrim($this->apiUrl, '/') . '/' . ltrim($method, '/');
        $response = $this->httpClient->request('POST', $url, [
            'body' => $params,
            'timeout' => 30,
        ]);

        $content = $response->getContent(false);
        $decoded = json_decode($content, true);
        if (!is_array($decoded)) {
            throw new \RuntimeException('Alfa acquiring: invalid JSON response from ' . $method . ': ' . $content);
        }

        return $decoded;
    }
}
