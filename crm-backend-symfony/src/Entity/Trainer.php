<?php

namespace App\Entity;

use App\Entity\Contract\TenantAware;
use App\Entity\Trait\OrganizationOwnedTrait;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity]
#[ORM\Table(name: 'trainers')]
class Trainer implements TenantAware
{
    use OrganizationOwnedTrait;

    public const STATUS_PUBLISHED = 'published';
    public const STATUS_MODERATION = 'moderation';
    public const STATUS_HIDDEN = 'hidden';

    /** @var list<string> */
    public const PUBLICATION_STATUSES = [
        self::STATUS_PUBLISHED,
        self::STATUS_MODERATION,
        self::STATUS_HIDDEN,
    ];

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column(type: 'integer')]
    private ?int $id = null;

    #[ORM\Column(type: 'string', length: 150)]
    private string $name;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $specialization = null;

    #[ORM\Column(type: 'float', nullable: true)]
    private ?float $rating = null;

    #[ORM\Column(type: 'string', length: 255, nullable: true)]
    private ?string $photoUrl = null;

    #[ORM\Column(type: 'string', length: 50, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $description = null;

    /**
     * Видимость в клиентском приложении:
     * published — в списке «Наша команда»;
     * moderation — скрыт (ожидает модерации);
     * hidden — скрыт вручную.
     */
    /** Новые карточки по умолчанию на модерации; миграция оставляет существующих published. */
    #[ORM\Column(name: 'publication_status', type: 'string', length: 20, options: ['default' => self::STATUS_MODERATION])]
    private string $publicationStatus = self::STATUS_MODERATION;

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

    public function getSpecialization(): ?string
    {
        return $this->specialization;
    }

    public function setSpecialization(?string $specialization): self
    {
        $this->specialization = $specialization;
        return $this;
    }

    public function getRating(): ?float
    {
        return $this->rating;
    }

    public function setRating(?float $rating): self
    {
        $this->rating = $rating;
        return $this;
    }

    public function getPhotoUrl(): ?string
    {
        return $this->photoUrl;
    }

    public function setPhotoUrl(?string $photoUrl): self
    {
        $this->photoUrl = $photoUrl;
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

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
        return $this;
    }

    public function getPublicationStatus(): string
    {
        return $this->publicationStatus;
    }

    public function setPublicationStatus(string $publicationStatus): self
    {
        $status = strtolower(trim($publicationStatus));
        if (!\in_array($status, self::PUBLICATION_STATUSES, true)) {
            $status = self::STATUS_HIDDEN;
        }
        $this->publicationStatus = $status;

        return $this;
    }

    public function isPublishedInApp(): bool
    {
        return $this->publicationStatus === self::STATUS_PUBLISHED;
    }

    public static function normalizePublicationStatus(mixed $raw, string $default = self::STATUS_MODERATION): string
    {
        if (!\is_string($raw)) {
            return $default;
        }
        $status = strtolower(trim($raw));

        return \in_array($status, self::PUBLICATION_STATUSES, true) ? $status : $default;
    }
}
