<?php

namespace App\Entity;

use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'promotions')]
class Promotion
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 120)]
    private string $title;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $subtitle = null;

    #[ORM\Column(type: 'string', length: 50)]
    private string $buttonText = 'Подробнее';

    /** shop | subscriptions | none */
    #[ORM\Column(type: 'string', length: 20)]
    private string $actionType = 'shop';

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $actionValue = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $imagePath = null;

    #[ORM\Column(type: 'string', length: 9)]
    private string $bgFrom = '#F97316';

    #[ORM\Column(type: 'string', length: 9)]
    private string $bgTo = '#3B82F6';

    #[ORM\Column(type: 'integer')]
    private int $sortOrder = 100;

    #[ORM\Column(type: 'boolean')]
    private bool $isActive = true;

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable();
    }

    public function getId(): ?int { return $this->id; }
    public function getTitle(): string { return $this->title; }
    public function setTitle(string $title): self { $this->title = $title; return $this; }

    public function getSubtitle(): ?string { return $this->subtitle; }
    public function setSubtitle(?string $subtitle): self { $this->subtitle = $subtitle; return $this; }

    public function getButtonText(): string { return $this->buttonText; }
    public function setButtonText(string $buttonText): self { $this->buttonText = $buttonText; return $this; }

    public function getActionType(): string { return $this->actionType; }
    public function setActionType(string $actionType): self { $this->actionType = $actionType; return $this; }

    public function getActionValue(): ?string { return $this->actionValue; }
    public function setActionValue(?string $actionValue): self { $this->actionValue = $actionValue; return $this; }

    public function getImagePath(): ?string { return $this->imagePath; }
    public function setImagePath(?string $imagePath): self { $this->imagePath = $imagePath; return $this; }

    public function getBgFrom(): string { return $this->bgFrom; }
    public function setBgFrom(string $bgFrom): self { $this->bgFrom = $bgFrom; return $this; }

    public function getBgTo(): string { return $this->bgTo; }
    public function setBgTo(string $bgTo): self { $this->bgTo = $bgTo; return $this; }

    public function getSortOrder(): int { return $this->sortOrder; }
    public function setSortOrder(int $sortOrder): self { $this->sortOrder = $sortOrder; return $this; }

    public function isActive(): bool { return $this->isActive; }
    public function setIsActive(bool $isActive): self { $this->isActive = $isActive; return $this; }

    public function getCreatedAt(): \DateTimeImmutable { return $this->createdAt; }
}

