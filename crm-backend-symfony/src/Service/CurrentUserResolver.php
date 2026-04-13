<?php

namespace App\Service;

use App\Entity\User;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;

/**
 * Определяет текущего пользователя API по заголовку X-User-Id или токену.
 * Приложение после логина/регистрации должно передавать X-User-Id: user-123 (или просто 123).
 */
class CurrentUserResolver
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {}

    public function resolve(Request $request): ?User
    {
        // Заголовок X-User-Id: user-123 или 123
        $header = $request->headers->get('X-User-Id');
        if ($header !== null && $header !== '') {
            $userId = str_starts_with($header, 'user-') ? (int) substr($header, 5) : (int) $header;
            if ($userId > 0) {
                $user = $this->em->getRepository(User::class)->find($userId);
                if ($user !== null && !$user->isBlocked()) {
                    return $user;
                }
                return null; // blocked or not found
            }
        }

        return null;
    }
}
