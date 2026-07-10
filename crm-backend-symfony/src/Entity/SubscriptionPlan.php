<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'subscription_plans')]
class SubscriptionPlan implements TenantAware
{
    use OrganizationOwnedTrait;
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    #[ORM\Column(type: 'float')]
    private float $price;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $durationDays = null;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $visitsCount = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $type = 'unlimited'; // unlimited, limited, personal

    #[ORM\Column(type: 'boolean')]
    private bool $isPopular = false;

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

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
        return $this;
    }

    public function getPrice(): float
    {
        return $this->price;
    }

    public function setPrice(float $price): self
    {
        $this->price = $price;
        return $this;
    }

    public function getDurationDays(): ?int
    {
        return $this->durationDays;
    }

    public function setDurationDays(?int $durationDays): self
    {
        $this->durationDays = $durationDays;
        return $this;
    }

    public function getVisitsCount(): ?int
    {
        return $this->visitsCount;
    }

    public function setVisitsCount(?int $visitsCount): self
    {
        $this->visitsCount = $visitsCount;
        return $this;
    }

    public function getType(): string
    {
        return $this->type;
    }

    public function setType(string $type): self
    {
        $this->type = $type;
        return $this;
    }

    public function isPopular(): bool
    {
        return $this->isPopular;
    }

    public function setIsPopular(bool $isPopular): self
    {
        $this->isPopular = $isPopular;
        return $this;
    }
}

