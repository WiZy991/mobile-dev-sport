<?php

namespace App\Service;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;

/**
 * Текущий клиент мобильного API — только по валидному Bearer access-токену.
 * Заголовок X-User-Id игнорируется (раньше позволял подмену пользователя).
 */
class CurrentUserResolver
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function resolve(Request $request): ?User
    {
        $auth = $this->resolveAuthState($request);

        return $auth['user'];
    }

    /**
     * @return array{user: ?User, code: ?string, message: ?string}
     */
    public function resolveAuthState(Request $request): array
    {
        $token = $this->extractBearerToken($request);
        if ($token === null || $token === '') {
            return [
                'user' => null,
                'code' => 'missing_token',
                'message' => 'Войдите в приложение, чтобы продолжить.',
            ];
        }

        $user = $this->em->getRepository(User::class)->findOneBy(['apiAccessToken' => $token]);
        if ($user === null) {
            return [
                'user' => null,
                'code' => 'invalid_token',
                'message' => 'Сессия недействительна. Войдите в приложение снова.',
            ];
        }

        if ($user->isBlocked()) {
            return [
                'user' => null,
                'code' => 'user_blocked',
                'message' => 'Аккаунт заблокирован. Обратитесь в клуб.',
            ];
        }

        $expires = $user->getApiAccessTokenExpiresAt();
        if ($expires !== null && $expires < new \DateTimeImmutable()) {
            return [
                'user' => null,
                'code' => 'token_expired',
                'message' => 'Сессия истекла. Войдите в приложение снова.',
            ];
        }

        return ['user' => $user, 'code' => null, 'message' => null];
    }

    private function extractBearerToken(Request $request): ?string
    {
        $auth = $request->headers->get('Authorization', '');
        if (preg_match('/Bearer\s+(\S+)/i', $auth, $m)) {
            return trim($m[1], " \t\"'");
        }

        return null;
    }
}
