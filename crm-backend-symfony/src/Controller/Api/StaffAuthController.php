<?php

namespace App\Controller\Api;

use App\Entity\StaffNotification;
use App\Entity\StaffUser;
use App\Entity\Trainer;
use App\Service\Api\StaffMobileAuthTokenIssuer;
use App\Service\Staff\StaffEventNotifier;
use App\Service\Staff\StaffOnboardingService;
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
        private readonly StaffEventNotifier $staffEventNotifier,
        private readonly StaffOnboardingService $onboarding,
    ) {
    }

    #[Route('/register', name: 'api_staff_auth_register', methods: ['POST'])]
    public function register(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $name = trim((string) ($data['name'] ?? 'Новый тренер'));
        $password = (string) ($data['password'] ?? '');

        if ($email === '' || $password === '') {
            return $this->json(['error' => 'Укажите email и password', 'code' => 'missing_credentials'], 400);
        }
        if (!filter_var($email, \FILTER_VALIDATE_EMAIL)) {
            return $this->json(['error' => 'Некорректный email', 'code' => 'invalid_email'], 400);
        }

        $trainer = $this->findTrainerByEmail($email);
        if ($trainer instanceof Trainer) {
            return $this->registerClaimingTrainer($trainer, $email, $name, $password);
        }

        $existing = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if ($existing !== null) {
            return $this->json(['error' => 'Email уже зарегистрирован', 'code' => 'email_already_exists'], 409);
        }

        $user = (new StaffUser())
            ->setEmail($email)
            ->setName($name !== '' ? $name : 'Новый тренер')
            ->setRoles(['ROLE_TRAINER'])
            ->setRegistrationStatus(StaffUser::REGISTRATION_PENDING)
            ->setIsActive(true);
        $user->setPassword($this->passwordHasher->hashPassword($user, $password));

        $this->em->persist($user);
        $this->em->flush();

        $this->staffEventNotifier->notifyAdmins(
            StaffNotification::TYPE_STAFF_REGISTRATION,
            'Заявка тренера на регистрацию',
            sprintf('%s (%s) хочет зарегистрироваться как тренер', $user->getName(), $user->getEmail()),
            $user->getId() !== null ? (string) $user->getId() : null,
        );

        $issued = $this->tokens->issue($user, true);
        $issued['onboarding'] = $this->onboarding->serialize($user);

        return $this->json($issued, 201);
    }

    /**
     * Email совпал с карточкой тренера в CRM → сразу одобренный StaffUser + вход.
     */
    private function registerClaimingTrainer(
        Trainer $trainer,
        string $email,
        string $name,
        string $password,
    ): JsonResponse {
        $linked = $this->em->getRepository(StaffUser::class)->findOneBy(['trainer' => $trainer]);
        $byEmail = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);

        if ($linked instanceof StaffUser && $byEmail instanceof StaffUser && $linked->getId() !== $byEmail->getId()) {
            return $this->json([
                'error' => 'Эта карточка тренера уже привязана к другому аккаунту',
                'code' => 'trainer_already_linked',
            ], 409);
        }

        $user = $linked ?? $byEmail;
        $isNew = false;
        if (!$user instanceof StaffUser) {
            $isNew = true;
            $user = (new StaffUser())
                ->setEmail($email)
                ->setRoles(['ROLE_TRAINER']);
            $this->em->persist($user);
        }

        $displayName = $name !== '' && $name !== 'Новый тренер'
            ? $name
            : ($trainer->getName() !== '' ? $trainer->getName() : $email);

        $user
            ->setEmail($email)
            ->setName($displayName)
            ->setRoles(['ROLE_TRAINER'])
            ->setRegistrationStatus(StaffUser::REGISTRATION_APPROVED)
            ->setIsActive(true)
            ->setTrainer($trainer);

        if ($trainer->getOrganization() !== null) {
            $user->setOrganization($trainer->getOrganization());
        }
        if ($trainer->getEmail() === null || $trainer->getEmail() === '') {
            $trainer->setEmail($email);
        }

        $user->setPassword($this->passwordHasher->hashPassword($user, $password));
        $this->em->flush();

        $issued = $this->tokens->issue($user, $isNew);
        $issued['onboarding'] = $this->onboarding->serialize($user);
        $issued['claimed_trainer'] = true;

        return $this->json($issued, $isNew ? 201 : 200);
    }

    private function findTrainerByEmail(string $email): ?Trainer
    {
        /** @var Trainer|null $trainer */
        $trainer = $this->em->createQueryBuilder()
            ->select('t')
            ->from(Trainer::class, 't')
            ->where('LOWER(t.email) = :email')
            ->setParameter('email', mb_strtolower($email))
            ->setMaxResults(1)
            ->getQuery()
            ->getOneOrNullResult();

        return $trainer instanceof Trainer ? $trainer : null;
    }

    #[Route('/login', name: 'api_staff_auth_login', methods: ['POST'])]
    public function login(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true) ?? [];
        $email = mb_strtolower(trim((string) ($data['email'] ?? '')));
        $password = (string) ($data['password'] ?? '');

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['email' => $email]);
        if ($user === null) {
            return $this->json(['error' => 'Неверный email или пароль', 'code' => 'invalid_credentials'], 401);
        }
        if ($user->getRegistrationStatus() === StaffUser::REGISTRATION_REJECTED || !$user->isActive()) {
            return $this->json([
                'error' => $user->getRegistrationStatus() === StaffUser::REGISTRATION_REJECTED
                    ? 'Регистрация отклонена администратором'
                    : 'Неверный email или пароль',
                'code' => $user->getRegistrationStatus() === StaffUser::REGISTRATION_REJECTED
                    ? 'registration_rejected'
                    : 'invalid_credentials',
            ], 401);
        }
        if (!$this->passwordHasher->isPasswordValid($user, $password)) {
            return $this->json(['error' => 'Неверный email или пароль', 'code' => 'invalid_credentials'], 401);
        }

        $issued = $this->tokens->issue($user, false);
        $issued['onboarding'] = $this->onboarding->serialize($user);

        return $this->json($issued);
    }

    #[Route('/refresh', name: 'api_staff_auth_refresh', methods: ['POST'])]
    public function refresh(Request $request): JsonResponse
    {
        $refresh = $this->extractBearerToken($request);
        if ($refresh === null || $refresh === '') {
            return $this->json(['error' => 'Требуется refresh-токен', 'code' => 'missing_refresh'], 401);
        }

        $user = $this->em->getRepository(StaffUser::class)->findOneBy(['apiRefreshToken' => $refresh]);
        if ($user === null || !$user->isActive()
            || $user->getRegistrationStatus() === StaffUser::REGISTRATION_REJECTED
        ) {
            return $this->json(['error' => 'Недействительный refresh-токен', 'code' => 'invalid_refresh'], 401);
        }

        $issued = $this->tokens->issue($user, false);
        $issued['onboarding'] = $this->onboarding->serialize($user);

        return $this->json($issued);
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
