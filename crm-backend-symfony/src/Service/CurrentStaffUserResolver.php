<?php

namespace App\Service;

use App\Entity\StaffUser;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\HttpFoundation\Request;

class CurrentStaffUserResolver
{
    public function __construct(
        private readonly EntityManagerInterface $em,
    ) {
    }

    public function resolve(Request $request): ?StaffUser
    {
        $token = $this->extractBearerToken($request);
        if ($token === null || $token === '') {
            return null;
        }

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['apiAccessToken' => $token]);
        if ($user === null || !$user->isActive()) {
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
