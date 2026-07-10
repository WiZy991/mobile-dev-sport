<?php

declare(strict_types=1);

namespace App\Service\Tenant;

use App\Entity\Club;
use App\Entity\Organization;
use App\Entity\StaffUser;
use App\Repository\OrganizationRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

final class OrganizationProvisioner
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly OrganizationRepository $organizations,
        private readonly UserPasswordHasherInterface $passwordHasher,
    ) {
    }

    /**
     * @return array{organization: Organization, admin: StaffUser, club: Club}
     */
    public function provision(
        string $name,
        string $slug,
        string $adminEmail,
        string $adminPassword,
        string $adminName = 'Администратор',
        ?string $orgEmail = null,
        ?string $orgPhone = null,
        int $demoDays = 14,
        string $tariff = 'demo',
        ?\DateTimeImmutable $subscriptionStartsAt = null,
        ?\DateTimeImmutable $subscriptionEndsAt = null,
    ): array {
        $slug = self::normalizeSlug($slug);
        if ($slug === '') {
            throw new \InvalidArgumentException('Укажите slug организации (латиница, цифры, дефис).');
        }

        if ($this->organizations->findOneBySlug($slug) !== null) {
            throw new \InvalidArgumentException('Организация с таким slug уже существует.');
        }

        if ($this->em->getRepository(StaffUser::class)->findOneBy(['email' => $adminEmail])) {
            throw new \InvalidArgumentException('Email администратора уже занят.');
        }

        $subscriptionStartsAt ??= new \DateTimeImmutable('today');
        $subscriptionEndsAt ??= $subscriptionStartsAt->modify('+' . max(1, $demoDays) . ' days');
        if ($subscriptionEndsAt <= $subscriptionStartsAt) {
            throw new \InvalidArgumentException('Дата окончания подписки должна быть позже даты начала.');
        }

        $organization = (new Organization())
            ->setName(trim($name))
            ->setSlug($slug)
            ->setEmail($orgEmail)
            ->setPhone($orgPhone)
            ->setTariff($tariff)
            ->setIsActive(true)
            ->setSubscriptionStartsAt($subscriptionStartsAt)
            ->setSubscriptionEndsAt($subscriptionEndsAt);

        $club = (new Club())
            ->setOrganization($organization)
            ->setName($organization->getName())
            ->setAddress('Укажите адрес в настройках')
            ->setPhone($orgPhone)
            ->setEmail($orgEmail);

        $admin = (new StaffUser())
            ->setOrganization($organization)
            ->setEmail($adminEmail)
            ->setName($adminName)
            ->setRoles(['ROLE_SUPER_ADMIN'])
            ->setIsActive(true);
        $admin->setPassword($this->passwordHasher->hashPassword($admin, $adminPassword));

        $this->em->persist($organization);
        $this->em->persist($club);
        $this->em->persist($admin);
        $this->em->flush();

        return [
            'organization' => $organization,
            'admin' => $admin,
            'club' => $club,
        ];
    }

    public static function normalizeSlug(string $slug): string
    {
        $slug = mb_strtolower(trim($slug));
        $slug = (string) preg_replace('/[^a-z0-9-]+/', '-', $slug);
        $slug = trim($slug, '-');

        return $slug;
    }

    public static function slugFromName(string $name): string
    {
        $translit = [
            'а' => 'a', 'б' => 'b', 'в' => 'v', 'г' => 'g', 'д' => 'd', 'е' => 'e', 'ё' => 'e',
            'ж' => 'zh', 'з' => 'z', 'и' => 'i', 'й' => 'y', 'к' => 'k', 'л' => 'l', 'м' => 'm',
            'н' => 'n', 'о' => 'o', 'п' => 'p', 'р' => 'r', 'с' => 's', 'т' => 't', 'у' => 'u',
            'ф' => 'f', 'х' => 'h', 'ц' => 'c', 'ч' => 'ch', 'ш' => 'sh', 'щ' => 'sch',
            'ъ' => '', 'ы' => 'y', 'ь' => '', 'э' => 'e', 'ю' => 'yu', 'я' => 'ya',
        ];
        $lower = mb_strtolower($name);
        $out = '';
        $len = mb_strlen($lower);
        for ($i = 0; $i < $len; ++$i) {
            $ch = mb_substr($lower, $i, 1);
            $out .= $translit[$ch] ?? $ch;
        }

        return self::normalizeSlug($out);
    }
}
