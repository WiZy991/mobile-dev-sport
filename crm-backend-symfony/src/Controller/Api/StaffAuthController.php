<?php

namespace App\Controller\Api;

use App\Entity\StaffUser;
use App\Service\Admin\AdminMenuBuilder;
use App\Service\Api\StaffMobileAuthTokenIssuer;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/api/v1/staff/auth')]
final class StaffAuthController extends AbstractController
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly UserPasswordHasherInterface $passwordHasher,
        private readonly StaffMobileAuthTokenIssuer $tokens,
        private readonly AdminMenuBuilder $adminMenuBuilder,
    ) {
    }

    #[Route('/register', name: 'api_staff_auth_register', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = trim((string) ($data['email'] ?? ''));
        $name = trim((string) ($data['name'] ?? 'Новый сотрудник'));
        $password = (string) ($data['password'] ?? '');
        $role = trim((string) ($data['role'] ?? 'ROLE_VIEWER'));

        if ($email === '' || $password === '') {
            return $this->json(['error' => 'Укажите email и password', 'code' => 'missing_credentials'], 400);
        }
        if (!in_array($role, $this->adminMenuBuilder->supportedRoles(), true)) {
            return $this->json(['error' => 'Недопустимая роль', 'code' => 'invalid_role'], 400);
        }

        $existing = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if ($existing !== null) {
            return $this->json(['error' => 'Email уже зарегистрирован', 'code' => 'email_already_exists'], 409);
        }

        $user = (new StaffUser())
            ->setEmail($email)
            ->setName($name)
            ->setRoles([$role])
            ->setIsActive(true);
        $user->setPassword($this->passwordHasher->hashPassword($user, $password));

        $this->em->persist($user);
        $this->em->flush();

        return $this->json($this->tokens->issue($user, true));
    }

    #[Route('/login', name: 'api_staff_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = trim((string) ($data['email'] ?? ''));
        $password = (string) ($data['password'] ?? '');

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if ($user === null || !$user->isActive()) {
            return $this->json(['error' => 'Неверные данные', 'code' => 'invalid_credentials'], 401);
        }
        if (!$this->passwordHasher->isPasswordValid($user, $password)) {
            return $this->json(['error' => 'Неверные данные', 'code' => 'invalid_credentials'], 401);
        }

        return $this->json($this->tokens->issue($user, false));
    }

    #[Route('/refresh', name: 'api_staff_auth_refresh', methods: ['POST'])]
    public function refresh(Request $request): JsonResponse
    {
        $refresh = $this->extractBearerToken($request);
        if ($refresh === null || $refresh === '') {
            return $this->json(['error' => 'Требуется refresh-токен', 'code' => 'missing_refresh'], 401);
        }

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['apiRefreshToken' => $refresh]);
        if ($user === null || !$user->isActive()) {
            return $this->json(['error' => 'Недействительный refresh-токен', 'code' => 'invalid_refresh'], 401);
        }

        return $this->json($this->tokens->issue($user, false));
    }

    #[Route('/logout', name: 'api_staff_auth_logout', methods: ['POST'])]
    public function logout(Request $request): JsonResponse
    {
        $bearer = $this->extractBearerToken($request);
        if ($bearer !== null && $bearer !== '') {
            $user = $this->em->getRepository(StaffUser::class)->findOneBy(['apiAccessToken' => $bearer])
                ?? $this->em->getRepository(StaffUser::class)->findOneBy(['apiRefreshToken' => $bearer]);
            if ($user instanceof StaffUser) {
                $this->tokens->revokeSession($user);
            }
        }

        return $this->json(['success' => true]);
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
