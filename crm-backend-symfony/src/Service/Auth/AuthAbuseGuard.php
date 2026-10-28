<?php

declare(strict_types=1);

namespace App\Service\Auth;

use Psr\Cache\CacheItemPoolInterface;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;

/**
 * Капча не всегда: только после частых запросов с одного IP
 * (логин, SMS, регистрация, сброс пароля).
 */
final class AuthAbuseGuard
{
    /** @var array<string, array{window: int, captcha_after: int, block_after: int}> */
    private const RULES = [
        'otp' => ['window' => 600, 'captcha_after' => 4, 'block_after' => 18],
        'login' => ['window' => 600, 'captcha_after' => 6, 'block_after' => 20],
        'register' => ['window' => 600, 'captcha_after' => 4, 'block_after' => 12],
        'password_forgot' => ['window' => 3600, 'captcha_after' => 2, 'block_after' => 8],
        'staff_login' => ['window' => 600, 'captcha_after' => 5, 'block_after' => 15],
        'global' => ['window' => 60, 'captcha_after' => 12, 'block_after' => 40],
    ];

    public function __construct(
        private readonly CacheItemPoolInterface $cache,
        private readonly CaptchaChallengeService $captcha,
    ) {
    }

    public function enforceFromRequest(Request $request, string $action): ?JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        if (!\is_array($data)) {
            $data = $request->request->all();
        }

        return $this->enforce(
            $request,
            $action,
            isset($data['captcha_id']) ? (string) $data['captcha_id'] : null,
            isset($data['captcha_answer']) ? (string) $data['captcha_answer'] : null,
        );
    }

    public function enforce(Request $request, string $action, ?string $captchaId, ?string $captchaAnswer): ?JsonResponse
    {
        $ip = ClientIp::from($request);
        $this->hit($ip, $action);
        $this->hit($ip, 'global');

        if ($this->isBlocked($ip, $action) || $this->isBlocked($ip, 'global')) {
            return new JsonResponse([
                'error' => 'Слишком много попыток. Подождите несколько минут и попробуйте снова.',
                'code' => 'rate_limited',
            ], 429);
        }

        // Капча временно отключена — оставляем только лимит частых запросов.
        return null;
    }

    /** Ошибка для веб-форм или null, если можно продолжать. */
    public function enforceWeb(Request $request, string $action, ?string $captchaId, ?string $captchaAnswer): ?string
    {
        $json = $this->enforce($request, $action, $captchaId, $captchaAnswer);
        if ($json === null) {
            return null;
        }
        $payload = json_decode((string) $json->getContent(), true);

        return is_array($payload) && isset($payload['error'])
            ? (string) $payload['error']
            : 'Слишком много попыток. Подождите несколько минут.';
    }

    /** @return array{id: string, question: string}|null */
    public function challengeIfNeeded(Request $request, string $action): ?array
    {
        return null;
    }

    public function needsCaptchaForIp(string $ip, string $action): bool
    {
        return $this->needsCaptcha($ip, $action) || $this->needsCaptcha($ip, 'global');
    }

    public function isBlockedForIp(string $ip, string $action): bool
    {
        return $this->isBlocked($ip, $action) || $this->isBlocked($ip, 'global');
    }

    public function hit(string $ip, string $action): int
    {
        $rule = self::RULES[$action] ?? self::RULES['global'];
        $item = $this->cache->getItem($this->key($ip, $action));
        $count = $item->isHit() ? (int) $item->get() : 0;
        ++$count;
        $item->set($count);
        $item->expiresAfter($rule['window']);
        $this->cache->save($item);

        return $count;
    }

    private function needsCaptcha(string $ip, string $action): bool
    {
        $rule = self::RULES[$action] ?? self::RULES['global'];
        $count = $this->count($ip, $action);

        return $count >= $rule['captcha_after'];
    }

    private function isBlocked(string $ip, string $action): bool
    {
        $rule = self::RULES[$action] ?? self::RULES['global'];

        return $this->count($ip, $action) >= $rule['block_after'];
    }

    private function count(string $ip, string $action): int
    {
        $item = $this->cache->getItem($this->key($ip, $action));

        return $item->isHit() ? (int) $item->get() : 0;
    }

    private function key(string $ip, string $action): string
    {
        return 'auth.abuse.' . $action . '.' . md5($ip);
    }
}
