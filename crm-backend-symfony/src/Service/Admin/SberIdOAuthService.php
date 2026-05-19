<?php

namespace App\Service\Admin;

use Symfony\Contracts\HttpClient\HttpClientInterface;

/**
 * OIDC-клиент для Сбер ID.
 * Для продакшена рекомендуется проверка подписи id_token (JWKS).
 * Запросы к oauth.sber.ru согласно документации Сбера выполняются с клиентским TLS-сертификатом (PKCS12), см. SBER_ID_MTLS_PKCS12.
 */
final class SberIdOAuthService
{
    private string $clientId;

    private string $clientSecret;

    public function __construct(
        private readonly HttpClientInterface $httpClient,
        string $clientId,
        string $clientSecret,
        private readonly string $authorizeUrl,
        private readonly string $tokenUrl,
        private readonly ?string $userInfoUrl,
        private readonly bool $verifySsl = true,
        private readonly string $mtlsPkcs12Path = '',
        private readonly string $mtlsPkcs12Password = '',
    ) {
        $this->clientId = trim($clientId);
        $this->clientSecret = trim($clientSecret);
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

        $response = $this->httpClient->request('POST', $this->tokenUrl, $this->withTlsOptions([
            'headers' => [
                'Content-Type' => 'application/x-www-form-urlencoded',
                'Accept' => 'application/json',
                'rquid' => $rquid,
            ],
            'body' => http_build_query($body),
        ]));

        $data = $response->toArray(false);
        if (($data['error'] ?? null) !== null) {
            throw new \RuntimeException('Сбер ID token error: ' . self::stringifyTokenErrorMessage($data));
        }

        return $data;
    }

    /**
     * У Сбера поля error / error_description иногда приходят объектом/массивом в JSON — строковая конкатенация давала Warning.
     *
     * @param array<string, mixed> $data
     */
    private static function stringifyTokenErrorMessage(array $data): string
    {
        $parts = [];
        foreach (['error_description', 'error'] as $key) {
            if (!array_key_exists($key, $data)) {
                continue;
            }
            $v = $data[$key];
            if (is_string($v)) {
                $parts[] = trim($v);

                continue;
            }
            if (is_scalar($v)) {
                $parts[] = (string) $v;

                continue;
            }
            if ($v !== null) {
                $encoded = json_encode($v, JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE);
                if ($encoded !== false && $encoded !== '') {
                    $parts[] = $encoded;
                }
            }
        }

        $merged = trim(implode(' — ', array_filter($parts, static fn ($s) => $s !== '')), " \t\n\r\0\x0B—");

        return $merged !== '' ? $merged : 'unknown_error';
    }

    /** @return array<string, mixed> */
    public function fetchUserInfo(string $accessToken): array
    {
        $url = trim((string) ($this->userInfoUrl ?? ''));
        if ($url === '') {
            return [];
        }

        $rquid = strtoupper(bin2hex(random_bytes(16)));
        $response = $this->httpClient->request('GET', $url, $this->withTlsOptions([
            'headers' => [
                'Accept' => 'application/json',
                'Authorization' => 'Bearer ' . $accessToken,
                'rquid' => $rquid,
            ],
        ]));

        $data = $response->toArray(false);

        return is_array($data) ? $data : [];
    }

    /**
     * @param array<string, mixed> $options
     *
     * @return array<string, mixed>
     */
    private function withTlsOptions(array $options): array
    {
        if (!$this->verifySsl) {
            $options = array_merge($options, [
                'verify_peer' => false,
                'verify_host' => false,
            ]);
        }

        $pkcs12 = trim($this->mtlsPkcs12Path);
        if ($pkcs12 !== '') {
            // См. https://developers.sber.ru/docs/ru/sberid/faq/a2-work-with-certificates — без клиентского P12 oauth.sber.ru часто отвечает 401.
            $curlTls = [
                \CURLOPT_SSLCERTTYPE => 'P12',
                \CURLOPT_SSLCERT => $pkcs12,
            ];
            $pass = $this->mtlsPkcs12Password;
            if ($pass !== '') {
                $curlTls[\CURLOPT_SSLCERTPASSWD] = $pass;
            }
            $extra = $options['extra'] ?? [];
            $extra['curl'] = array_merge($extra['curl'] ?? [], $curlTls);
            $options['extra'] = $extra;
        }

        return $options;
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
