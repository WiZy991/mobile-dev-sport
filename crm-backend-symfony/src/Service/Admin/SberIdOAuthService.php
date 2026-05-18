<?php

namespace App\Service\Admin;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * OIDC-клиент для Сбер ID.
 * Для продакшена рекомендуется проверка подписи id_token (JWKS).
 */
final class SberIdOAuthService
{
    public function __construct(
        private readonly HttpClientInterface $httpClient,
        private readonly string $clientId,
        private readonly string $clientSecret,
        private readonly string $authorizeUrl,
        private readonly string $tokenUrl,
        private readonly ?string $userInfoUrl,
    ) {
    }

    public function isConfigured(): bool
    {
        return $this->clientId !== '' && $this->clientSecret !== '';
    }

    /**
     * Авторизация без PKCE (CRM / legacy mobile redirect на HTTPS callback).
     */
    public function buildAuthorizeUrl(string $redirectUri, string $state, string $nonce): string
    {
        return $this->buildAuthorizeUrlInternal($redirectUri, $state, $nonce, null, null, 'openid');
    }

    /**
     * Авторизация с PKCE (нативное приложение).
     */
    public function buildAuthorizeUrlWithPkce(
        string $redirectUri,
        string $state,
        string $nonce,
        string $codeChallenge,
        string $codeChallengeMethod,
        string $scope = 'openid profile email mobile',
    ): string {
        return $this->buildAuthorizeUrlInternal(
            $redirectUri,
            $state,
            $nonce,
            $codeChallenge,
            $codeChallengeMethod,
            $scope,
        );
    }

    private function buildAuthorizeUrlInternal(
        string $redirectUri,
        string $state,
        string $nonce,
        ?string $codeChallenge,
        ?string $codeChallengeMethod,
        string $scope,
    ): string {
        $params = [
            'response_type' => 'code',
            'client_id' => $this->clientId,
            'redirect_uri' => $redirectUri,
            'scope' => $scope,
            'state' => $state,
            'nonce' => $nonce,
        ];
        if ($codeChallenge !== null && $codeChallenge !== '' && $codeChallengeMethod !== null && $codeChallengeMethod !== '') {
            $params['code_challenge'] = $codeChallenge;
            $params['code_challenge_method'] = $codeChallengeMethod;
        }

        $q = http_build_query($params, '', '&', PHP_QUERY_RFC3986);

        return $this->authorizeUrl . (str_contains($this->authorizeUrl, '?') ? '&' : '?') . $q;
    }

    /**
     * @return array{id_token?: string, access_token?: string, token_type?: string, ...}
     */
    public function exchangeAuthorizationCode(string $code, string $redirectUri, ?string $codeVerifier = null): array
    {
        $body = [
            'grant_type' => 'authorization_code',
            'code' => $code,
            'redirect_uri' => $redirectUri,
            'client_id' => $this->clientId,
            'client_secret' => $this->clientSecret,
        ];
        if ($codeVerifier !== null && $codeVerifier !== '') {
            $body['code_verifier'] = $codeVerifier;
        }

        $rquid = strtoupper(bin2hex(random_bytes(16)));

        $response = $this->httpClient->request('POST', $this->tokenUrl, [
            'headers' => [
                'Content-Type' => 'application/x-www-form-urlencoded',
                'Accept' => 'application/json',
                'rquid' => $rquid,
            ],
            'body' => http_build_query($body),
        ]);

        $data = $response->toArray(false);
        if (($data['error'] ?? null) !== null) {
            throw new \RuntimeException('Сбер ID token error: ' . ($data['error_description'] ?? $data['error']));
        }

        return $data;
    }

    /** @return array<string, mixed> */
    public function fetchUserInfo(string $accessToken): array
    {
        $url = trim((string) ($this->userInfoUrl ?? ''));
        if ($url === '') {
            return [];
        }

        $rquid = strtoupper(bin2hex(random_bytes(16)));
        $response = $this->httpClient->request('GET', $url, [
            'headers' => [
                'Accept' => 'application/json',
                'Authorization' => 'Bearer ' . $accessToken,
                'rquid' => $rquid,
            ],
        ]);

        $data = $response->toArray(false);

        return is_array($data) ? $data : [];
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
