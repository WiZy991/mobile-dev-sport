<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: \App\Repository\ClubSettingRepository::class)]
#[ORM\Table(name: 'club_settings')]
#[ORM\UniqueConstraint(name: 'uniq_club_setting_org_key', columns: ['organization_id', 'setting_key'])]
class ClubSetting implements TenantAware
{
    use OrganizationOwnedTrait;

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(name: 'setting_key', type: 'string', length: 50)]
    private string $settingKey;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $settingValue = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getSettingKey(): string
    {
        return $this->settingKey;
    }

    public function setSettingKey(string $settingKey): self
    {
        $this->settingKey = $settingKey;

        return $this;
    }

    public function getSettingValue(): ?string
    {
        return $this->settingValue;
    }

    public function setSettingValue(?string $settingValue): self
    {
        $this->settingValue = $settingValue;

        return $this;
    }
}
