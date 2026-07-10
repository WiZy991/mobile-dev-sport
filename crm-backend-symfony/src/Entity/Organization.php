<?php

namespace App\Entity;

use App\Repository\OrganizationRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: OrganizationRepository::class)]
#[ORM\Table(name: 'organizations')]
#[ORM\UniqueConstraint(name: 'uniq_organization_slug', columns: ['slug'])]
class Organization
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    /** URL-safe идентификатор: fitpower, yoga-studio */
    #[ORM\Column(type: 'string', length: 80)]
    private string $slug;

    #[ORM\Column(type: 'string', length: 180, nullable: true)]
    private ?string $email = null;

    #[ORM\Column(type: 'string', length: 32, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(type: 'string', length: 20, nullable: true)]
    private ?string $inn = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $tariff = 'demo';

    #[ORM\Column(type: 'boolean')]
    private bool $isActive = true;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $demoUntil = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $subscriptionStartsAt = null;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
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

    public function getSlug(): string
    {
        return $this->slug;
    }

    public function setSlug(string $slug): self
    {
        $this->slug = $slug;

        return $this;
    }

    public function getEmail(): ?string
    {
        return $this->email;
    }

    public function setEmail(?string $email): self
    {
        $this->email = $email;

        return $this;
    }

    public function getPhone(): ?string
    {
        return $this->phone;
    }

    public function setPhone(?string $phone): self
    {
        $this->phone = $phone;

        return $this;
    }

    public function getInn(): ?string
    {
        return $this->inn;
    }

    public function setInn(?string $inn): self
    {
        $this->inn = $inn;

        return $this;
    }

    public function getTariff(): string
    {
        return $this->tariff;
    }

    public function setTariff(string $tariff): self
    {
        $this->tariff = $tariff;

        return $this;
    }

    public function isActive(): bool
    {
        return $this->isActive;
    }

    public function setIsActive(bool $isActive): self
    {
        $this->isActive = $isActive;

        return $this;
    }

    public function getDemoUntil(): ?\DateTimeImmutable
    {
        return $this->demoUntil;
    }

    public function setDemoUntil(?\DateTimeImmutable $demoUntil): self
    {
        $this->demoUntil = $demoUntil;

        return $this;
    }

    public function isDemoExpired(): bool
    {
        return $this->demoUntil !== null && $this->demoUntil < new \DateTimeImmutable();
    }

    public function getSubscriptionStartsAt(): ?\DateTimeImmutable
    {
        return $this->subscriptionStartsAt;
    }

    public function setSubscriptionStartsAt(?\DateTimeImmutable $subscriptionStartsAt): self
    {
        $this->subscriptionStartsAt = $subscriptionStartsAt;

        return $this;
    }

    public function getSubscriptionEndsAt(): ?\DateTimeImmutable
    {
        return $this->demoUntil;
    }

    public function setSubscriptionEndsAt(?\DateTimeImmutable $subscriptionEndsAt): self
    {
        $this->demoUntil = $subscriptionEndsAt;

        return $this;
    }

    public function isSubscriptionExpired(): bool
    {
        return $this->demoUntil !== null && $this->demoUntil < new \DateTimeImmutable();
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
}
