<?php

namespace App\Controller\Api;

use App\Entity\User;
use App\Service\Admin\SberIdOAuthService;
use App\Service\Api\MobileAuthTokenIssuer;
use App\Service\Api\SberMobileAuthService;
use App\Service\Api\SberOAuthPkceStateService;
use App\Service\CurrentUserResolver;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

class SberAuthController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly CurrentUserResolver $userResolver,
        private readonly SberMobileAuthService $mobileAuth,
        private readonly SberIdOAuthService $sberId,
        private readonly SberOAuthPkceStateService $pkceState,
        private readonly MobileAuthTokenIssuer $mobileTokens,
        private readonly string $nativeRedirectUri,
        private readonly string $nativeAppBridgeUri,
        private readonly string $sberClientId,
    ) {
    }

    /** Ссылка на авторизацию Сбер ID для текущего пользователя приложения (legacy HTTPS callback). */
    public function authorizeUrl(Request $request): JsonResponse
    {
        $user = $this->userResolver->resolve($request);
        if (!$user) {
            return $this->json(['error' => 'Unauthorized'], 401);
        }
        if (!$this->mobileAuth->isReady()) {
            return $this->json([
                'error' => 'sber_not_configured',
                'message' => 'Сбер ID для мобильного приложения не настроен на сервере (SBER_ID_*, SBER_ID_MOBILE_REDIRECT_URI).',
            ], 503);
        }
        try {
            $url = $this->mobileAuth->buildAuthorizeUrlForUser($user);
        } catch (\Throwable $e) {
            return $this->json(['error' => 'sber_url_failed', 'message' => $e->getMessage()], 500);
        }

        return $this->json(['authorize_url' => $url]);
    }

    /**
     * Нативный поток PKCE: параметры для сборки авторизации (GET).
     * Query: code_challenge (required), code_challenge_method (default S256), redirect_uri (required).
     */
    public function loginGet(Request $request): JsonResponse
    {
        return $this->buildLoginJsonResponse($request->query->all());
    }

    /**
     * То же, что GET /login, но параметры в JSON (удобнее для клиента).
     */
    public function loginPost(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);

        return $this->buildLoginJsonResponse(is_array($data) ? $data : []);
    }

    /**
     * Нативный callback: приложение передаёт authorization code + code_verifier.
     *
     * @param array<string, mixed> $params
     */
    private function buildLoginJsonResponse(array $params): JsonResponse
    {
        if (!$this->sberId->isConfigured()) {
            return $this->json([
                'error' => 'sber_not_configured',
                'message' => 'Задайте SBER_ID_CLIENT_ID и SBER_ID_CLIENT_SECRET.',
            ], 503);
        }
        $expectedRedirect = trim($this->nativeRedirectUri);
        if ($expectedRedirect === '') {
            return $this->json([
                'error' => 'sber_native_redirect_missing',
                'message' => 'Укажите SBER_ID_NATIVE_REDIRECT_URI — HTTPS из кабинета Сбер ID (например https://…/api/v1/auth/sber/callback). Без второго URI в кабинете используйте SBER_ID_NATIVE_APP_BRIDGE_URI (deep link на приложение).',
            ], 503);
        }

        $challenge = isset($params['code_challenge']) && is_string($params['code_challenge'])
            ? trim($params['code_challenge']) : '';
        if ($challenge === '') {
            return $this->json(['error' => 'missing_code_challenge'], 400);
        }
        $method = isset($params['code_challenge_method']) && is_string($params['code_challenge_method'])
            ? trim($params['code_challenge_method']) : 'S256';
        $redirectUri = isset($params['redirect_uri']) && is_string($params['redirect_uri'])
            ? trim($params['redirect_uri']) : '';
        if ($redirectUri === '' || $redirectUri !== $expectedRedirect) {
            return $this->json(['error' => 'invalid_redirect_uri', 'message' => 'redirect_uri должен совпадать с SBER_ID_NATIVE_REDIRECT_URI'], 400);
        }

        $issued = $this->pkceState->issue();
        $authorizeUrl = $this->sberId->buildAuthorizeUrlWithPkce(
            $redirectUri,
            $issued['state'],
            $issued['nonce'],
            $challenge,
            $method,
        );

        return $this->json(['authorize_url' => $authorizeUrl]);
    }

    /**
     * Обмен кода на сессию приложения + обновление профиля из Сбер ID.
     */
    public function nativeCallback(Request $request): JsonResponse
    {
        if (!$this->sberId->isConfigured()) {
            return $this->json(['error' => 'sber_not_configured'], 503);
        }
        $expectedRedirect = trim($this->nativeRedirectUri);
        if ($expectedRedirect === '') {
            return $this->json(['error' => 'sber_native_redirect_missing'], 503);
        }

        $data = json_decode($request->getContent(), true) ?? [];
        $code = isset($data['code']) && is_string($data['code']) ? trim($data['code']) : '';
        $verifier = isset($data['code_verifier']) && is_string($data['code_verifier']) ? trim($data['code_verifier']) : '';
        $redirectUri = isset($data['redirect_uri']) && is_string($data['redirect_uri']) ? trim($data['redirect_uri']) : '';
        $state = isset($data['state']) && is_string($data['state']) ? trim($data['state']) : '';

        if ($code === '' || $verifier === '') {
            return $this->json(['error' => 'missing_code_or_verifier'], 400);
        }
        if ($redirectUri === '' || $redirectUri !== $expectedRedirect) {
            return $this->json(['error' => 'invalid_redirect_uri'], 400);
        }
        if ($state === '') {
            return $this->json(['error' => 'missing_state'], 400);
        }

        $verifiedState = $this->pkceState->verify($state);
        if ($verifiedState === null) {
            return $this->json(['error' => 'invalid_state'], 400);
        }
        $expectedNonce = $verifiedState['nonce'];

        try {
            $tokens = $this->sberId->exchangeAuthorizationCode($code, $redirectUri, $verifier);
        } catch (\Throwable $e) {
            return $this->json(['error' => 'token_exchange_failed', 'message' => $e->getMessage()], 502);
        }

        $idToken = $tokens['id_token'] ?? null;
        if (!is_string($idToken) || $idToken === '') {
            return $this->json(['error' => 'missing_id_token'], 502);
        }

        $claims = $this->sberId->decodeIdTokenPayload($idToken);
        $sub = isset($claims['sub']) && is_string($claims['sub']) ? $claims['sub'] : null;
        if ($sub === null || $sub === '') {
            return $this->json(['error' => 'missing_sub'], 502);
        }

        if (!$this->assertAudienceMatchesClient($claims)) {
            return $this->json(['error' => 'invalid_aud'], 400);
        }

        $nonceClaim = $claims['nonce'] ?? null;
        if (!is_string($nonceClaim) || !hash_equals($expectedNonce, $nonceClaim)) {
            return $this->json(['error' => 'nonce_mismatch'], 400);
        }

        $accessToken = isset($tokens['access_token']) && is_string($tokens['access_token']) ? $tokens['access_token'] : '';
        $userinfo = $accessToken !== '' ? $this->sberId->fetchUserInfo($accessToken) : [];

        $merged = array_merge($claims, $userinfo);

        /** @var User|null $sessionUser */
        $sessionUser = $this->userResolver->resolve($request);

        try {
            $user = $this->resolveOrCreateUserForSber($sessionUser, $sub, $merged);
        } catch (\RuntimeException $e) {
            return $this->json(['error' => 'account_conflict', 'message' => $e->getMessage()], 409);
        }

        $this->applySberProfile($user, $merged);
        $this->markSberVerified($user, $claims, $sub);

        $this->em->persist($user);
        $this->em->flush();

        return $this->json($this->mobileTokens->issue($user, true));
    }

    /**
     * Redirect URI мобильного потока (legacy): браузер открывает этот GET после OAuth.
     */
    public function callback(Request $request): Response
    {
        $state = (string) $request->query->get('state', '');
        // PKCE: в кабинете Сбер ID может быть только HTTPS redirect — приходим сюда с code/state,
        // затем перенаправляем на кастомную схему для завершения ASWebAuthenticationSession.
        if ($state !== '' && $this->pkceState->verify($state) !== null) {
            $bridge = trim($this->nativeAppBridgeUri);
            if ($bridge !== '') {
                return new RedirectResponse($this->buildNativeAppBridgeUrl($bridge, $request));
            }
        }

        $userId = $this->mobileAuth->parseUserIdFromState($state);
        if ($userId === null) {
            return new Response(
                $this->htmlResult(false, 'Неверный или устаревший запрос. Откройте приложение и начните верификацию снова.'),
                400
            );
        }

        /** @var User|null $user */
        $user = $this->em->getRepository(User::class)->find($userId);
        if (!$user) {
            return new Response($this->htmlResult(false, 'Пользователь не найден.'), 404);
        }

        if ($request->query->get('error')) {
            return new Response(
                $this->htmlResult(false, 'Сбер ID: ' . (string) $request->query->get('error_description', $request->query->get('error'))),
                400
            );
        }

        $code = $request->query->get('code');
        if (!is_string($code) || $code === '') {
            return new Response($this->htmlResult(false, 'Нет кода авторизации.'), 400);
        }

        $redirect = $this->mobileAuth->getMobileRedirectUri();
        try {
            $tokens = $this->sberId->exchangeAuthorizationCode($code, $redirect);
        } catch (\Throwable $e) {
            return new Response($this->htmlResult(false, 'Не удалось завершить вход: ' . htmlspecialchars($e->getMessage(), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8')), 502);
        }

        $idToken = $tokens['id_token'] ?? null;
        if (!is_string($idToken) || $idToken === '') {
            return new Response($this->htmlResult(false, 'Ответ Сбер ID без id_token.'), 502);
        }

        $claims = $this->sberId->decodeIdTokenPayload($idToken);
        $sub = isset($claims['sub']) && is_string($claims['sub']) ? $claims['sub'] : null;

        $accessToken = isset($tokens['access_token']) && is_string($tokens['access_token']) ? $tokens['access_token'] : '';
        $userinfo = $accessToken !== '' ? $this->sberId->fetchUserInfo($accessToken) : [];
        $merged = array_merge($claims, $userinfo);
        $this->applySberProfile($user, $merged);

        $this->markSberVerified($user, $claims, $sub);
        $this->em->flush();

        return new Response($this->htmlResult(true, 'Верификация успешна. Вернитесь в приложение и нажмите «Купить» ещё раз — затем откроется оплата (когда будет подключён эквайринг).'));
    }

    private function buildNativeAppBridgeUrl(string $bridgeBase, Request $request): string
    {
        $params = $request->query->all();
        $qs = http_build_query($params, '', '&', PHP_QUERY_RFC3986);
        if ($qs === '') {
            return $bridgeBase;
        }

        $sep = str_contains($bridgeBase, '?') ? '&' : '?';

        return $bridgeBase . $sep . $qs;
    }

    private function htmlResult(bool $ok, string $message): string
    {
        $title = $ok ? 'Готово' : 'Ошибка';
        $safe = htmlspecialchars($message, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');

        return <<<HTML
<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{$title}</title>
<style>body{font-family:sans-serif;padding:24px;max-width:480px;margin:auto;line-height:1.5}</style></head>
<body><h1>{$title}</h1><p>{$safe}</p></body></html>
HTML;
    }

    /**
     * @param array<string, mixed> $merged
     */
    private function resolveOrCreateUserForSber(?User $sessionUser, string $sub, array $merged): User
    {
        $repo = $this->em->getRepository(User::class);

        $bySub = $repo->findOneBy(['sberId' => $sub]);
        if ($bySub !== null) {
            if ($sessionUser !== null && $sessionUser->getId() !== $bySub->getId()) {
                throw new \RuntimeException('Этот Сбер ID уже привязан к другому аккаунту.');
            }

            return $bySub;
        }

        if ($sessionUser !== null) {
            return $sessionUser;
        }

        $email = $this->pickEmail($merged);
        $phone = $this->pickPhone($merged);

        $candidates = [];
        if ($email !== null) {
            $candidates[] = $repo->findOneBy(['email' => $email]);
        }
        if ($phone !== null) {
            $candidates[] = $this->findUserByNormalizedPhone($phone);
        }

        foreach ($candidates as $candidate) {
            if ($candidate instanceof User) {
                $existingSber = $candidate->getSberId();
                if ($existingSber !== null && $existingSber !== '' && $existingSber !== $sub) {
                    throw new \RuntimeException('Найден профиль с тем же телефоном/email, но другим Сбер ID. Обратитесь в клуб.');
                }

                return $candidate;
            }
        }

        $user = new User();
        $user
            ->setEmail($email ?? $this->placeholderEmail($sub))
            ->setPhone($phone ?? '+7 900 000-00-00')
            ->setName($this->pickDisplayName($merged))
            ->setBonusPoints(0)
            ->setIsBlocked(false);

        return $user;
    }

    /** @param array<string, mixed> $merged */
    private function applySberProfile(User $user, array $merged): void
    {
        $email = $this->pickEmail($merged);
        if ($email !== null && filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $other = $this->em->getRepository(User::class)->findOneBy(['email' => $email]);
            if ($other === null || $other->getId() === $user->getId()) {
                $user->setEmail($email);
            }
        }

        $phone = $this->pickPhone($merged);
        if ($phone !== null && $phone !== '') {
            $user->setPhone($phone);
        }

        $name = $this->pickDisplayName($merged);
        if ($name !== '' && $name !== 'Клиент Сбер ID') {
            $user->setName($name);
        }
    }

    /** @param array<string, mixed> $claims */
    private function markSberVerified(User $user, array $claims, ?string $sub): void
    {
        $user
            ->setVerified(true)
            ->setSberId($sub)
            ->setPassportVerificationStatus('verified')
            ->setPassportVerifiedAt(new \DateTimeImmutable())
            ->setPassportVerificationProvider('sber_id')
            ->setPassportVerificationSubject($sub)
            ->setPassportVerificationAuditJson((string) json_encode($claims, JSON_UNESCAPED_UNICODE));
    }

    /** @param array<string, mixed> $merged */
    private function pickEmail(array $merged): ?string
    {
        foreach (['email', 'preferred_username'] as $k) {
            if (isset($merged[$k]) && is_string($merged[$k])) {
                $e = trim($merged[$k]);
                if ($e !== '') {
                    return $e;
                }
            }
        }

        return null;
    }

    /** @param array<string, mixed> $merged */
    private function pickPhone(array $merged): ?string
    {
        foreach (['phone_number', 'mobile', 'tel'] as $k) {
            if (isset($merged[$k]) && is_string($merged[$k])) {
                $p = trim($merged[$k]);
                if ($p !== '') {
                    return $this->formatRussianPhoneDigits($p);
                }
            }
        }

        return null;
    }

    /** @param array<string, mixed> $merged */
    private function pickDisplayName(array $merged): string
    {
        $family = isset($merged['family_name']) && is_string($merged['family_name']) ? trim($merged['family_name']) : '';
        $given = isset($merged['given_name']) && is_string($merged['given_name']) ? trim($merged['given_name']) : '';
        $middle = isset($merged['middle_name']) && is_string($merged['middle_name']) ? trim($merged['middle_name']) : '';

        $parts = array_filter([$family, $given, $middle], static fn($x) => $x !== '');
        if ($parts !== []) {
            return implode(' ', $parts);
        }
        if (isset($merged['name']) && is_string($merged['name'])) {
            $n = trim($merged['name']);
            if ($n !== '') {
                return $n;
            }
        }

        return 'Клиент Сбер ID';
    }

    private function placeholderEmail(string $sub): string
    {
        $hash = substr(hash('sha256', $sub), 0, 20);

        return 'sber-' . $hash . '@placeholder.worldfitness.local';
    }

    private function formatRussianPhoneDigits(string $raw): string
    {
        $digits = preg_replace('/\D+/', '', $raw) ?? '';
        if ($digits === '') {
            return $raw;
        }
        if (strlen($digits) === 10) {
            $digits = '7' . $digits;
        }
        if (strlen($digits) === 11 && $digits[0] === '8') {
            $digits = '7' . substr($digits, 1);
        }
        if (strlen($digits) !== 11 || $digits[0] !== '7') {
            return $raw;
        }
        $n = substr($digits, 1);

        return sprintf('+7 %s %s-%s-%s', substr($n, 0, 3), substr($n, 3, 3), substr($n, 6, 2), substr($n, 8, 2));
    }

    private function normalizePhoneKey(string $phone): string
    {
        return preg_replace('/\D+/', '', $phone) ?? '';
    }

    private function findUserByNormalizedPhone(string $phone): ?User
    {
        $needle = $this->normalizePhoneKey($phone);
        if ($needle === '') {
            return null;
        }

        foreach ($this->em->getRepository(User::class)->findAll() as $u) {
            if (!$u instanceof User) {
                continue;
            }
            $hay = $this->normalizePhoneKey($u->getPhone());
            if ($hay === $needle || (strlen($needle) >= 10 && str_ends_with($hay, substr($needle, -10)))) {
                return $u;
            }
        }

        return null;
    }

    /** @param array<string, mixed> $claims */
    private function assertAudienceMatchesClient(array $claims): bool
    {
        $cid = trim($this->sberClientId);
        if ($cid === '') {
            return false;
        }
        $aud = $claims['aud'] ?? null;
        if (is_string($aud)) {
            return trim($aud) === $cid;
        }
        if (is_array($aud)) {
            foreach ($aud as $a) {
                if (is_string($a) && trim($a) === $cid) {
                    return true;
                }
            }
        }

        return false;
    }
}
