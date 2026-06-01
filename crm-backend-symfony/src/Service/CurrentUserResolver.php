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
        $token = $this->extractBearerToken($request);
        if ($token === null || $token === '') {
            return null;
        }

        $user = $this->em->getRepository(User::class)->findOneBy(['apiAccessToken' => $token]);
        if ($user === null || $user->isBlocked()) {
            return null;
        }

        $expires = $user->getApiAccessTokenExpiresAt();
        if ($expires !== null && $expires < new \DateTimeImmutable()) {
            return null;
        }

        return $user;
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
