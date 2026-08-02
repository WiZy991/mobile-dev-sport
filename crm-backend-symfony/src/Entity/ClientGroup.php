<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'client_groups')]
class ClientGroup implements TenantAware
{
    use OrganizationOwnedTrait;

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 100)]
    private string $name;

    /** Скидка на абонементы в процентах (0–100). */
    #[ORM\Column(name: 'discount_percent', type: 'float')]
    private float $discountPercent = 0.0;

    #[ORM\OneToMany(mappedBy: 'clientGroup', targetEntity: User::class)]
    private Collection $users;

    public function __construct()
    {
        $this->users = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): self
    {
        $this->name = $name;

        return $this;
    }

    public function getDiscountPercent(): float
    {
        return $this->discountPercent;
    }

    public function setDiscountPercent(float $discountPercent): self
    {
        $this->discountPercent = max(0.0, min(100.0, $discountPercent));

        return $this;
    }

    /** @return Collection<int, User> */
    public function getUsers(): Collection
    {
        return $this->users;
    }
}
