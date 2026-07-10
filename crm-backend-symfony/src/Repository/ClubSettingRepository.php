<?php

namespace App\Repository;

use App\Entity\ClubSetting;
use App\Entity\Organization;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/** @extends ServiceEntityRepository<ClubSetting> */
class ClubSettingRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, ClubSetting::class);
    }

    public function findOneByOrganizationAndKey(Organization $organization, string $key): ?ClubSetting
    {
        return $this->findOneBy([
            'organization' => $organization,
            'settingKey' => $key,
        ]);
    }
}
