<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'promo_codes')]
class PromoCode implements TenantAware
{
    use OrganizationOwnedTrait;
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 50, unique: true)]
    private string $code;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $discountPercent = null;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $discountAmount = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $validFrom = null;

    #[ORM\Column(type: 'datetime_immutable', nullable: true)]
    private ?\DateTimeImmutable $validTo = null;

    #[ORM\Column(type: 'boolean')]
    private bool $isActive = true;

    #[ORM\Column(type: 'integer', nullable: true)]
    private ?int $usageLimit = null;

    #[ORM\Column(type: 'integer')]
    private int $usedCount = 0;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }

    public function getCode(): string { return $this->code; }
    public function setCode(string $code): self { $this->code = strtoupper($code); return $this; }

    public function getDiscountPercent(): ?float { return $this->discountPercent; }
    public function setDiscountPercent(?float $v): self { $this->discountPercent = $v; return $this; }

    public function getDiscountAmount(): ?float { return $this->discountAmount; }
    public function setDiscountAmount(?float $v): self { $this->discountAmount = $v; return $this; }

    public function getValidFrom(): ?\DateTimeImmutable { return $this->validFrom; }
    public function setValidFrom(?\DateTimeImmutable $v): self { $this->validFrom = $v; return $this; }

    public function getValidTo(): ?\DateTimeImmutable { return $this->validTo; }
    public function setValidTo(?\DateTimeImmutable $v): self { $this->validTo = $v; return $this; }

    public function isActive(): bool { return $this->isActive; }
    public function setIsActive(bool $v): self { $this->isActive = $v; return $this; }

    public function getUsageLimit(): ?int { return $this->usageLimit; }
    public function setUsageLimit(?int $v): self { $this->usageLimit = $v; return $this; }

    public function getUsedCount(): int { return $this->usedCount; }
    public function incrementUsedCount(): self { $this->usedCount++; return $this; }

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }

    public function isValid(): bool
    {
        if (!$this->isActive) return false;
        $now = new \DateTimeImmutable();
        if ($this->validFrom && $now < $this->validFrom) return false;
        if ($this->validTo && $now > $this->validTo) return false;
        if ($this->usageLimit !== null && $this->usedCount >= $this->usageLimit) return false;
        return true;
    }
}
