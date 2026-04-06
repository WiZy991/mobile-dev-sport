<?php

namespace App\Service\Admin;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * OIDC-клиент для Сбер ID (URL и параметры — уточнять по актуальной документации Сбера).
 * Проверка подписи id_token для продакшена нужно добавить отдельно (JWKS).
 */
final class SberIdOAuthService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $clientId,
        private readonly string $clientSecret,
        private readonly string $authorizeUrl,
        private readonly string $tokenUrl,
    ) {
    }

    public function isConfigured(): bool
    {
        return $this->clientId !== '' && $this->clientSecret !== '';
    }

    public function buildAuthorizeUrl(string $redirectUri, string $state, string $nonce): string
    {
        $q = http_build_query([
            'response_type' => 'code',
            'client_id' => $this->clientId,
            'redirect_uri' => $redirectUri,
            'scope' => 'openid',
            'state' => $state,
            'nonce' => $nonce,
        ], '', '&', PHP_QUERY_RFC3986);

        return $this->authorizeUrl . (str_contains($this->authorizeUrl, '?') ? '&' : '?') . $q;
    }

    /**
     * @return array{id_token?: string, access_token?: string, token_type?: string, ...}
     */
    public function exchangeAuthorizationCode(string $code, string $redirectUri): array
    {
        $response = $this->httpClient->request('POST', $this->tokenUrl, [
            'headers' => [
                'Content-Type' => 'application/x-www-form-urlencoded',
                'Authorization' => 'Basic ' . base64_encode($this->clientId . ':' . $this->clientSecret),
            ],
            'body' => http_build_query([
                'grant_type' => 'authorization_code',
                'code' => $code,
                'redirect_uri' => $redirectUri,
            ]),
        ]);

        $data = $response->toArray(false);
        if (($data['error'] ?? null) !== null) {
            throw new \RuntimeException('Сбер ID token error: ' . ($data['error_description'] ?? $data['error']));
        }

        return $data;
    }

    /** @return array<string, mixed> */
    public function decodeIdTokenPayload(string $jwt): array
    {
        $parts = explode('.', $jwt);
        if (count($parts) !== 3) {
            return [];
        }
        $b64 = strtr($parts[1], '-_', '+/');
        $json = base64_decode($b64, true);
        if ($json === false) {
            return [];
        }
        $data = json_decode($json, true);

        return is_array($data) ? $data : [];
    }
}
